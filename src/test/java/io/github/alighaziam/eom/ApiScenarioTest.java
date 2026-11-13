package io.github.alighaziam.eom;

import com.jayway.jsonpath.JsonPath;
import io.github.alighaziam.eom.outbox.application.OutboxService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiScenarioTest {

    @LocalServerPort private int port;
    @Autowired private OutboxService outbox;
    @Autowired private OutboxRepository outboxRepository;
    private RestClient http;

    @BeforeEach
    void client() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void deskSwaggerAndUnknownPathsAreServed() {
        String home = http.get().uri("/").retrieve().body(String.class);
        assertTrue(home.contains("Order desk"));
        assertTrue(home.contains("فارسی"));
        String messages = new String(http.get().uri("/assets/i18n.js").retrieve().body(byte[].class), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(messages.contains("Run checkout"));
        assertTrue(messages.contains("\u0627\u062c\u0631\u0627\u06cc \u062a\u0633\u0648\u06cc\u0647"));
        String script = http.get().uri("/assets/console.js").retrieve().body(String.class);
        assertTrue(script.contains("applyLocale"));
        String swagger = http.get().uri("/swagger-ui/index.html").retrieve().body(String.class);
        assertTrue(swagger.contains("Swagger UI"));
        String spec = http.get().uri("/v3/api-docs").retrieve().body(String.class);
        assertEquals("Enterprise Order Management", JsonPath.read(spec, "$.info.title"));
        assertTrue(spec.contains("/api/v1/checkouts"));
        assertTrue(spec.contains("/api/v1/checkouts/{id}/reconcile"));
        assertTrue(spec.contains("\"Customers\""));
        assertTrue(spec.contains("List customers"));
        String health = http.get().uri("/actuator/health").retrieve().body(String.class);
        assertEquals("UP", JsonPath.read(health, "$.status"));
        assertTrue(health.contains("deadLetters"));

        RestClientResponseException missing = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/missing-page").accept(MediaType.TEXT_HTML).retrieve().toBodilessEntity());
        assertEquals(404, missing.getStatusCode().value(), missing.getResponseBodyAsString());
        assertTrue(missing.getResponseBodyAsString().contains("Page not found"), missing.getResponseBodyAsString());
    }

    @Test
    void missingResourcesReturnNotFoundInsteadOfAServerError() {
        String id = UUID.randomUUID().toString();
        assertNotFound("/api/v1/customers/" + id);
        assertNotFound("/api/v1/products/" + id);
        assertNotFound("/api/v1/inventory/" + id);
        assertNotFound("/api/v1/orders/" + id);
        assertNotFound("/api/v1/payments/" + id);
        assertNotFound("/api/v1/shipments/" + id);
        assertNotFound("/api/v1/checkouts/" + id);
        RestClientResponseException replay = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/outbox/events/" + id + "/replay").retrieve().toBodilessEntity());
        assertEquals(404, replay.getStatusCode().value());
    }

    @Test
    void checkoutScenarioReservesStockCapturesPaymentAndCreatesAShipment() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String customer = post("/api/v1/customers", """
                {"fullName":"Scenario Customer","email":"scenario-%s@example.com"}
                """.formatted(suffix));
        String customerId = JsonPath.read(customer, "$.id");
        assertEquals("scenario-" + suffix + "@example.com", JsonPath.read(customer, "$.email"));

        String product = post("/api/v1/products", """
                {"sku":"SCN-%s","name":"Scenario Lamp","description":"Smoke scenario","unitPrice":18.50,"currency":"eur"}
                """.formatted(suffix));
        String productId = JsonPath.read(product, "$.id");
        assertEquals("SCN-" + suffix.toUpperCase(Locale.ROOT), JsonPath.read(product, "$.sku"));
        assertEquals("EUR", JsonPath.read(product, "$.currency"));

        String customerRead = http.get().uri("/api/v1/customers/" + customerId).retrieve().body(String.class);
        assertEquals("Scenario Customer", JsonPath.read(customerRead, "$.fullName"));
        assertTrue(http.get().uri("/api/v1/customers").retrieve().body(String.class).contains("scenario-" + suffix + "@example.com"));

        String stock = post("/api/v1/inventory/stock", """
                {"productId":"%s","quantity":6}
                """.formatted(productId));
        assertEquals(6, (Integer) JsonPath.read(stock, "$.availableQuantity"));

        String checkout = post("/api/v1/checkouts", """
                {
                  "customerId":"%s",
                  "lines":[{"productId":"%s","quantity":2}],
                  "recipientName":"Scenario Customer",
                  "addressLine1":"Harbor Street 12",
                  "city":"Berlin",
                  "postalCode":"10115",
                  "countryCode":"DE",
                  "phone":"+491234567",
                  "idempotencyKey":"scenario-%s"
                }
                """.formatted(customerId, productId, suffix));
        assertEquals("COMPLETED", JsonPath.read(checkout, "$.status"));
        String workflowId = JsonPath.read(checkout, "$.workflowId");
        String orderId = JsonPath.read(checkout, "$.orderId");
        String paymentId = JsonPath.read(checkout, "$.paymentId");
        String shipmentId = JsonPath.read(checkout, "$.shipmentId");

        assertEquals("COMPLETED", JsonPath.read(http.get().uri("/api/v1/checkouts/" + workflowId).retrieve().body(String.class), "$.status"));
        String order = http.get().uri("/api/v1/orders/" + orderId).retrieve().body(String.class);
        assertEquals("CONFIRMED", JsonPath.read(order, "$.status"));
        assertEquals(37.0, ((Number) JsonPath.read(order, "$.totalAmount")).doubleValue(), 0.001);
        assertEquals("CAPTURED", JsonPath.read(http.get().uri("/api/v1/payments/" + paymentId).retrieve().body(String.class), "$.status"));
        String shipment = http.get().uri("/api/v1/shipments/" + shipmentId).retrieve().body(String.class);
        assertEquals("PENDING", JsonPath.read(shipment, "$.status"));
        assertEquals("Berlin", JsonPath.read(shipment, "$.destination.city"));
        String stockAfter = http.get().uri("/api/v1/inventory/" + productId).retrieve().body(String.class);
        assertEquals(4, (Integer) JsonPath.read(stockAfter, "$.onHandQuantity"));
        assertEquals(4, (Integer) JsonPath.read(stockAfter, "$.availableQuantity"));
        assertTrue(http.get().uri("/api/v1/orders").retrieve().body(String.class).contains(orderId));
        assertTrue(http.get().uri("/api/v1/checkouts").retrieve().body(String.class).contains(workflowId));
    }

    @Test
    void deadLetterCanBeReplayedAndASecondReplayIsRejected() {
        UUID eventId = outbox.append("OrderPlaced", "Order", "order-1", 1, "scenario", null, "{}");
        OutboxEntity event = outboxRepository.findById(eventId).orElseThrow();
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        for (int attempt = 0; attempt < 12; attempt++) {
            event.failed("forced failure", now.plusSeconds(attempt));
        }
        outboxRepository.saveAndFlush(event);

        String replayed = http.post().uri("/api/v1/outbox/events/" + eventId + "/replay").retrieve().body(String.class);
        assertEquals(0, (Integer) JsonPath.read(replayed, "$.attempts"));
        assertNull(JsonPath.read(replayed, "$.deadLetteredAt"));
        assertNull(JsonPath.read(replayed, "$.publishedAt"));

        RestClientResponseException second = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/outbox/events/" + eventId + "/replay").retrieve().toBodilessEntity());
        assertEquals(409, second.getStatusCode().value());
        assertTrue(http.get().uri("/api/v1/outbox/events").retrieve().body(String.class).contains(eventId.toString()));
    }

    @Test
    void masterDataCanBeSearchedEditedAndDisabled() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String customer = post("/api/v1/customers", """
                {"fullName":"Page %s","email":"page-%s@example.com"}
                """.formatted(suffix, suffix));
        String customerId = JsonPath.read(customer, "$.id");
        String updated = http.put().uri("/api/v1/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"fullName":"Edited %s","email":"page-%s@example.com"}
                        """.formatted(suffix, suffix))
                .retrieve().body(String.class);
        assertEquals("Edited " + suffix, JsonPath.read(updated, "$.fullName"));
        String disabled = http.post().uri("/api/v1/customers/" + customerId + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"active\":false}")
                .retrieve().body(String.class);
        assertEquals(Boolean.FALSE, JsonPath.read(disabled, "$.active"));
        String page = http.get().uri(builder -> builder.path("/api/v1/customers")
                        .queryParam("size", 5)
                        .queryParam("active", false)
                        .queryParam("q", "page-" + suffix)
                        .build())
                .retrieve().body(String.class);
        assertEquals(1, ((Number) JsonPath.read(page, "$.totalElements")).intValue());

        String product = post("/api/v1/products", """
                {"sku":"page-%s","name":"Page lamp","description":"desk","unitPrice":12.50,"currency":"eur"}
                """.formatted(suffix));
        String productId = JsonPath.read(product, "$.id");
        String edited = http.put().uri("/api/v1/products/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Edited lamp\",\"description\":\"desk\",\"unitPrice\":15.00,\"currency\":\"EUR\"}")
                .retrieve().body(String.class);
        assertEquals("Edited lamp", JsonPath.read(edited, "$.name"));
        http.post().uri("/api/v1/products/" + productId + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"active\":false}")
                .retrieve().toBodilessEntity();
        post("/api/v1/inventory/stock", """
                {"productId":"%s","quantity":4}
                """.formatted(productId));
        http.put().uri("/api/v1/inventory/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"quantity\":9}")
                .retrieve().toBodilessEntity();
        String stock = http.get().uri("/api/v1/inventory/" + productId).retrieve().body(String.class);
        assertEquals(9, ((Number) JsonPath.read(stock, "$.onHandQuantity")).intValue());
    }

    private String post(String path, String json) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json).retrieve().body(String.class);
    }

    private void assertNotFound(String path) {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri(path).retrieve().toBodilessEntity());
        assertEquals(404, response.getStatusCode().value());
    }
}
