package io.github.alighaziam.eom.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provider boundary for payment authorization and lifecycle operations.
 * Every mutating call must pass its stable persisted idempotency key through
 * to the PSP. Implementations must never treat a timeout as a definitive decline.
 * The deterministic sandbox is the only configured implementation today.
 */
public interface PaymentGateway {
    String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);
    void capture(String reference, String idempotencyKey);
    void voidAuthorization(String reference, String idempotencyKey);
    void refund(String reference, String idempotencyKey);
}
