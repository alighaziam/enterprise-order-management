package io.github.alighaziam.eom.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe client-facing view of a persisted payment operation.
 * Provider references, idempotency keys, fingerprints, lease owners and internal errors are intentionally omitted.
 */
public record PaymentOperationView(
        UUID id,
        PaymentOperationType type,
        PaymentOperationStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
