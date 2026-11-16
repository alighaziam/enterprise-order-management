package io.github.alighaziam.eom.inventory;

import com.jayway.jsonpath.JsonPath;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false")
class InventoryConcurrencyIntegrationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private InventoryService inventoryService;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void concurrentReservationsCannotOversellStock() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String product = post("/api/v1/products",
                "{\"sku\":\"RACE-" + suffix + "\",\"name\":\"Concurrent Inventory Test\","
                        + "\"unitPrice\":5.00,\"currency\":\"EUR\"}");
        String productId = JsonPath.read(product, "$.id");

        post("/api/v1/inventory/stock",
                "{\"productId\":\"" + productId + "\",\"quantity\":5}");

        int contenders = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    try {
                        http.post().uri("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"productId\":\"" + productId + "\",\"quantity\":1}")
                                .retrieve().toBodilessEntity();
                        return 201;
                    } catch (RestClientResponseException rejected) {
                        return rejected.getStatusCode().value();
                    }
                }));
            }

            start.countDown();
            int accepted = 0;
            int rejected = 0;
            for (Future<Integer> attempt : attempts) {
                int status = attempt.get();
                if (status >= 200 && status < 300) {
                    accepted++;
                } else {
                    assertTrue(status >= 400 && status < 500,
                            "Unexpected server response during reservation race: " + status);
                    rejected++;
                }
            }

            assertEquals(5, accepted, "Only the five available units may be reserved");
            assertEquals(15, rejected, "Every excess reservation must be rejected");

            String stock = http.get().uri("/api/v1/inventory/" + productId)
                    .retrieve().body(String.class);
            assertEquals(5, ((Number) JsonPath.read(stock, "$.onHandQuantity")).intValue());
            assertEquals(5, ((Number) JsonPath.read(stock, "$.reservedQuantity")).intValue());
            assertEquals(0, ((Number) JsonPath.read(stock, "$.availableQuantity")).intValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReleaseOfSameReservationChangesReservedStockOnlyOnce() throws Exception {
        ReservationFixture fixture = createPendingReservation();
        int contenders = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Reservation>> attempts = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return inventoryService.release(fixture.reservationId());
                }));
            }
            start.countDown();
            for (Future<Reservation> attempt : attempts) {
                assertEquals(ReservationStatus.RELEASED, attempt.get().status());
            }

            assertStock(fixture.productId(), 5, 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentConfirmationOfSameReservationConsumesStockOnlyOnce() throws Exception {
        ReservationFixture fixture = createPendingReservation();
        int contenders = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Reservation>> attempts = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return inventoryService.confirm(fixture.reservationId());
                }));
            }
            start.countDown();
            for (Future<Reservation> attempt : attempts) {
                assertEquals(ReservationStatus.CONFIRMED, attempt.get().status());
            }

            assertStock(fixture.productId(), 4, 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReleaseAndConfirmationAllowExactlyOneTerminalTransition() throws Exception {
        ReservationFixture fixture = createPendingReservation();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationStatus> release = executor.submit(() -> {
                start.await();
                try {
                    return inventoryService.release(fixture.reservationId()).status();
                } catch (IllegalStateException losingTransition) {
                    return null;
                }
            });
            Future<ReservationStatus> confirm = executor.submit(() -> {
                start.await();
                try {
                    return inventoryService.confirm(fixture.reservationId()).status();
                } catch (IllegalStateException losingTransition) {
                    return null;
                }
            });

            start.countDown();
            ReservationStatus releaseResult = release.get();
            ReservationStatus confirmResult = confirm.get();
            assertTrue((releaseResult == ReservationStatus.RELEASED && confirmResult == null)
                            || (releaseResult == null && confirmResult == ReservationStatus.CONFIRMED),
                    "Exactly one competing terminal transition must win");

            if (releaseResult == ReservationStatus.RELEASED) {
                assertStock(fixture.productId(), 5, 0);
            } else {
                assertStock(fixture.productId(), 4, 0);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private ReservationFixture createPendingReservation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String product = post("/api/v1/products",
                "{\"sku\":\"LIFECYCLE-" + suffix + "\",\"name\":\"Reservation Lifecycle Test\","
                        + "\"unitPrice\":5.00,\"currency\":\"EUR\"}");
        String productId = JsonPath.read(product, "$.id");
        post("/api/v1/inventory/stock",
                "{\"productId\":\"" + productId + "\",\"quantity\":5}");
        String reservation = post("/api/v1/inventory/reservations",
                "{\"productId\":\"" + productId + "\",\"quantity\":1}");
        String reservationId = JsonPath.read(reservation, "$.id");
        return new ReservationFixture(UUID.fromString(productId), UUID.fromString(reservationId));
    }

    private void assertStock(UUID productId, int expectedOnHand, int expectedReserved) {
        String stock = http.get().uri("/api/v1/inventory/" + productId)
                .retrieve().body(String.class);
        assertNotNull(stock);
        assertEquals(expectedOnHand, ((Number) JsonPath.read(stock, "$.onHandQuantity")).intValue());
        assertEquals(expectedReserved, ((Number) JsonPath.read(stock, "$.reservedQuantity")).intValue());
        assertEquals(expectedOnHand - expectedReserved,
                ((Number) JsonPath.read(stock, "$.availableQuantity")).intValue());
    }

    private String post(String path, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
    }

    private record ReservationFixture(UUID productId, UUID reservationId) {}
}
