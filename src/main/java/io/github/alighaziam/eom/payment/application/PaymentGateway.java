package io.github.alighaziam.eom.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provider boundary for payment authorization and lifecycle operations.
 *
 * Implementations must propagate the stable idempotency key to the PSP and
 * must never treat a timeout as a definitive decline. The current service
 * invokes this synchronous contract from a transaction and is therefore
 * suitable for the deterministic sandbox only. A remote PSP adapter must
 * not be enabled until the payment workflow is changed to persisted,
 * split-phase commands with asynchronous outcome reconciliation.
 */
public interface PaymentGateway {
    String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey);
    void capture(String reference);
    void voidAuthorization(String reference);
    void refund(String reference);
}
