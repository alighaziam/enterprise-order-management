package io.github.alighaziam.eom.inventory;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false")
class InventoryConcurrencyIntegrationTest {
    @LocalServerPort
    private int port;

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

    private String post(String path, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
    }
}
