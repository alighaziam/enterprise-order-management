package io.github.alighaziam.eom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RestClient.Builder restClientBuilder;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = restClientBuilder.baseUrl("http://localhost:" + port).build();
        when(jwtDecoder.decode("orders-token")).thenReturn(jwt("orders-token", "orders.read"));
        when(jwtDecoder.decode("customers-token")).thenReturn(jwt("customers-token", "customers.read"));
    }

    @Test
    void rejectsRequestsWithoutBearerToken() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/customers").retrieve().toBodilessEntity());
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void rejectsValidTokenWithoutRequiredScope() {
        RestClientResponseException response = assertThrows(RestClientResponseException.class,
                () -> http.get().uri("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer orders-token")
                        .retrieve().toBodilessEntity());
        assertEquals(403, response.getStatusCode().value());
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
