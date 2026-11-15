package io.github.alighaziam.eom.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sandbox adapter contract. A real provider adapter must forward the stable
 * idempotency key to the provider for every retry of the same payment intent.
 */
public interface SandboxPaymentGateway {
    String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);
    void capture(String reference);
    void voidAuthorization(String reference);
    void refund(String reference);
}
