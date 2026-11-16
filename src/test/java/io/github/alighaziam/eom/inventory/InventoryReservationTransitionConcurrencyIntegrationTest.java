package io.github.alighaziam.eom.inventory;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false")
class InventoryReservationTransitionConcurrencyIntegrationTest {
    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void concurrentReleaseAndConfirmCanApplyOnlyOneTerminalTransition() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String product = post("/api/v1/products", """
                {"sku":"TRANSITION-%s","name":"Reservation Transition Test",
                 "unitPrice":5.00,"currency":"EUR"}
                """.formatted(suffix));
        String productId = JsonPath.read(product, "$.id");

        post("/api/v1/inventory/stock", """
                {"productId":"%s","quantity":5}
                """.formatted(productId));
        String reservation = post("/api/v1/inventory/reservations", """
                {"productId":"%s","quantity":2}
                """.formatted(productId));
        String reservationId = JsonPath.read(reservation, "$.reservationId");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> release = executor.submit(() -> {
                start.await();
                return postStatus("/api/v1/inventory/reservations/" + reservationId + "/release");
            });
            Future<Integer> confirm = executor.submit(() -> {
                start.await();
                return postStatus("/api/v1/inventory/reservations/" + reservationId + "/confirm");
            });

            start.countDown();
            int releaseStatus = release.get();
            int confirmStatus = confirm.get();

            assertTrue(isSuccess(releaseStatus) ^ isSuccess(confirmStatus),
                    "Exactly one competing terminal transition must succeed");
            assertEquals(409, isSuccess(releaseStatus) ? confirmStatus : releaseStatus,
                    "The losing transition must be rejected as a conflict");

            String stock = http.get().uri("/api/v1/inventory/" + productId)
                    .retrieve().body(String.class);
            int onHand = ((Number) JsonPath.read(stock, "$.onHandQuantity")).intValue();
            int reserved = ((Number) JsonPath.read(stock, "$.reservedQuantity")).intValue();
            int available = ((Number) JsonPath.read(stock, "$.availableQuantity")).intValue();

            if (isSuccess(releaseStatus)) {
                assertEquals(5, onHand, "Release must not consume on-hand stock");
                assertEquals(0, reserved, "Released stock must no longer be reserved");
                assertEquals(5, available);
            } else {
                assertEquals(3, onHand, "Confirm must consume the reserved quantity");
                assertEquals(0, reserved, "Confirmed stock must no longer be reserved");
                assertEquals(3, available);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String post(String path, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
    }

    private int postStatus(String path) {
        try {
            return http.post().uri(path).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException rejected) {
            return rejected.getStatusCode().value();
        }
    }

    private boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }
}
