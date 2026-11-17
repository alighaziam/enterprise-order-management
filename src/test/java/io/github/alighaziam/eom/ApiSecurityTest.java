package io.github.alighaziam.eom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.enabled=true",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.test/jwks"
        })
class ApiSecurityTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
        when(jwtDecoder.decode("orders-token")).thenReturn(jwt("orders-token", "orders.read"));
        when(jwtDecoder.decode("customers-token")).thenReturn(jwt("customers-token", "customers.read"));
        when(jwtDecoder.decode("operations-token")).thenReturn(jwt("operations-token", "operations.admin"));
        when(jwtDecoder.decode("outbox-read-token")).thenReturn(jwt("outbox-read-token", "outbox.read"));
    }

    @Test
    void rejectsRequestsWithoutBearerToken() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/customers").retrieve().toBodilessEntity());
        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getResponseBodyAsString().contains("\"status\":401"));
        assertTrue(response.getResponseBodyAsString().contains("Authentication is required."));
    }

    @Test
    void bearerApiDoesNotCreateHttpSessions() {
        var cookies = http.get().uri("/api/v1/customers").exchange((request, response) -> {
            assertEquals(401, response.getStatusCode().value());
            return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        });
        assertTrue(cookies.stream().noneMatch(cookie -> cookie.startsWith("JSESSIONID=")));
    }

    @Test
    void rejectsValidTokenWithoutRequiredScope() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer orders-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, response.getStatusCode().value());
        assertTrue(response.getResponseBodyAsString().contains("required scope"));
    }

    @Test
    void readScopeDoesNotAuthorizeWriteOperations() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customers-token")
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("{\"fullName\":\"Security Test\",\"email\":\"security@example.test\"}")
                        .retrieve().toBodilessEntity());
        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void matchingReadScopePassesAuthorizationAndReachesController() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/customers/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customers-token")
                        .retrieve().toBodilessEntity());
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void publicHealthRemainsAvailableButActuatorMetricsRequireOperationsScope() {
        String health = http.get().uri("/actuator/health").retrieve().body(String.class);
        assertTrue(health.contains("\"status\":\"UP\""));

        RestClientResponseException forbidden = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer orders-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, forbidden.getStatusCode().value());

        String metrics = http.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer operations-token")
                .retrieve().body(String.class);
        assertTrue(metrics.contains("\"names\""));
    }

    @Test
    void unmatchedApiRoutesRemainDeniedEvenForAuthenticatedUsers() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/unmapped")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customers-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void operatorAuditHistoryRequiresOperationsAdminScope() {
        RestClientResponseException forbidden = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/operations/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customers-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, forbidden.getStatusCode().value());

        String audit = http.get().uri("/api/v1/operations/audit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer operations-token")
                .retrieve().body(String.class);
        assertTrue(audit.contains("\"content\":"));
    }

    @Test
    void outboxReplayRequiresOperationsAdminScope() {
        String eventId = UUID.randomUUID().toString();
        RestClientResponseException forbidden = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/outbox/events/" + eventId + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customers-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, forbidden.getStatusCode().value());

        RestClientResponseException notFound = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/outbox/events/" + eventId + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operations-token")
                        .retrieve().toBodilessEntity());
        assertEquals(404, notFound.getStatusCode().value());

        String audit = http.get().uri("/api/v1/operations/audit?q=" + eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer operations-token")
                .retrieve().body(String.class);
        assertTrue(audit.contains("OUTBOX_REPLAY"));
        assertTrue(audit.contains("FAILED"));
        assertTrue(audit.contains("security-test"));
    }


    @Test
    void outboxReadScopeCannotReplayDeadLetterEvents() {
        RestClientResponseException forbidden = assertThrows(RestClientResponseException.class,
                () -> http.post().uri("/api/v1/outbox/events/" + UUID.randomUUID() + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer outbox-read-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, forbidden.getStatusCode().value());
    }

    private static Jwt jwt(String token, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("sub", "security-test")
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
