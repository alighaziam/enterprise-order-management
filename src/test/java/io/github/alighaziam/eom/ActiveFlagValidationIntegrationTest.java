package io.github.alighaziam.eom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.security.enabled=false")
class ActiveFlagValidationIntegrationTest {
    @LocalServerPort private int port;
    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void activeEndpointsRequireAnExplicitBoolean() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String customer = post("/api/v1/customers",
                "{\"fullName\":\"Active Test\",\"email\":\"active-" + suffix + "@example.com\"}");
        String customerId = JsonPath.read(customer, "$.id");
        String product = post("/api/v1/products",
                "{\"sku\":\"ACT-" + suffix + "\",\"name\":\"Active Test\",\"unitPrice\":12.50,\"currency\":\"EUR\"}");
        String productId = JsonPath.read(product, "$.id");

        RestClientResponseException customerMissing = assertThrows(RestClientResponseException.class,
                () -> post("/api/v1/customers/" + customerId + "/active", "{}"));
        assertEquals(400, customerMissing.getStatusCode().value());

        RestClientResponseException productMissing = assertThrows(RestClientResponseException.class,
                () -> post("/api/v1/products/" + productId + "/active", "{}"));
        assertEquals(400, productMissing.getStatusCode().value());

        String customerStillActive = http.get().uri("/api/v1/customers/" + customerId).retrieve().body(String.class);
        String productStillActive = http.get().uri("/api/v1/products/" + productId).retrieve().body(String.class);
        assertEquals(Boolean.TRUE, JsonPath.read(customerStillActive, "$.active"));
        assertEquals(Boolean.TRUE, JsonPath.read(productStillActive, "$.active"));
    }

    private String post(String path, String body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
    }
}