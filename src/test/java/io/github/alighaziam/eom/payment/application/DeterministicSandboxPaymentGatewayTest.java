package io.github.alighaziam.eom.payment.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicSandboxPaymentGatewayTest {

    private final DeterministicSandboxPaymentGateway gateway = new DeterministicSandboxPaymentGateway();

    @Test
    void acceptsStableKeysForEveryMutatingOperation() {
        UUID paymentId = UUID.randomUUID();
        String reference = gateway.authorize(paymentId, new java.math.BigDecimal("10.00"), "EUR", "auth-key");
        assertEquals("sandbox-" + paymentId, reference);

        assertDoesNotThrow(() -> gateway.capture(reference, "capture-key"));
        assertDoesNotThrow(() -> gateway.voidAuthorization(reference, "void-key"));
        assertDoesNotThrow(() -> gateway.refund(reference, "refund-key"));
    }

    @Test
    void rejectsMissingOrOversizedLifecycleIdempotencyKeys() {
        assertThrows(IllegalArgumentException.class, () -> gateway.capture("sandbox-ref", " "));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.voidAuthorization("sandbox-ref", "x".repeat(121)));
        assertThrows(IllegalArgumentException.class, () -> gateway.refund("sandbox-ref", null));
    }
}
