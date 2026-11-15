package io.github.alighaziam.eom.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous sandbox adapter contract. A real PSP integration must move to a
 * persisted split-phase command/outcome flow so network calls do not hold database
 * locks, and must forward the stable idempotency key for every retry.
 */
public interface SandboxPaymentGateway {
    String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);
    void capture(String reference);
    void voidAuthorization(String reference);
    void refund(String reference);
}
