package io.github.alighaziam.eom.inventory;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false")
class InventoryQuantityValidationIntegrationTest {
    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void stockEndpointsRequireAnExplicitQuantity() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String product = post("/api/v1/products",
                "{\"sku\":\"INV-" + suffix + "\",\"name\":\"Inventory Test\",\"unitPrice\":5.00,\"currency\":\"EUR\"}");
        String productId = JsonPath.read(product, "$.id");

        post("/api/v1/inventory/stock",
                "{\"productId\":\"" + productId + "\",\"quantity\":5}");

        RestClientResponseException adjustmentMissing = assertThrows(RestClientResponseException.class,
                () -> put("/api/v1/inventory/" + productId, "{}"));
        assertEquals(400, adjustmentMissing.getStatusCode().value());

        RestClientResponseException initializationMissing = assertThrows(RestClientResponseException.class,
                () -> post("/api/v1/inventory/stock",
                        "{\"productId\":\"" + UUID.randomUUID() + "\"}"));
        assertEquals(400, initializationMissing.getStatusCode().value());

        RestClientResponseException reservationMissing = assertThrows(RestClientResponseException.class,
                () -> post("/api/v1/inventory/reservations",
                        "{\"productId\":\"" + productId + "\"}"));
        assertEquals(400, reservationMissing.getStatusCode().value());

        String unchanged = http.get().uri("/api/v1/inventory/" + productId).retrieve().body(String.class);
        assertEquals(5, ((Number) JsonPath.read(unchanged, "$.onHandQuantity")).intValue());
        assertEquals(0, ((Number) JsonPath.read(unchanged, "$.reservedQuantity")).intValue());
    }

    private String post(String path, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
    }

    private String put(String path, String body) {
        return http.put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(String.class);
    }
}
