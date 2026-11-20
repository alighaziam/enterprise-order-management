package io.github.alighaziam.eom.payment.infrastructure;

import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentGatewayOperationRepository
        extends JpaRepository<PaymentGatewayOperationEntity, UUID> {

    @Modifying
    @Query(value = """
            WITH candidates AS (
                SELECT id
                FROM payment.gateway_operations
                WHERE status = 'PENDING'
                  AND next_attempt_at <= :now
                  AND (locked_until IS NULL OR locked_until < :now)
                ORDER BY next_attempt_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE payment.gateway_operations AS operation
               SET status = 'PROCESSING',
                   locked_by = :workerId,
                   locked_until = :leaseUntil,
                   attempts = operation.attempts + 1,
                   updated_at = :now
              FROM candidates
             WHERE operation.id = candidates.id
            """, nativeQuery = true)
    int claimPendingBatch(@Param("workerId") String workerId,
                          @Param("now") Instant now,
                          @Param("leaseUntil") Instant leaseUntil,
                          @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            WITH candidates AS (
                SELECT id
                FROM payment.gateway_operations
                WHERE status = 'UNKNOWN'
                  AND next_attempt_at <= :now
                ORDER BY next_attempt_at, updated_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE payment.gateway_operations AS operation
               SET status = 'RECONCILING',
                   locked_by = :workerId,
                   locked_until = :leaseUntil,
                   attempts = operation.attempts + 1,
                   updated_at = :now
              FROM candidates
             WHERE operation.id = candidates.id
            """, nativeQuery = true)
    int claimUnknownBatch(@Param("workerId") String workerId,
                          @Param("now") Instant now,
                          @Param("leaseUntil") Instant leaseUntil,
                          @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            UPDATE payment.gateway_operations
               SET status = 'UNKNOWN',
                   locked_by = NULL,
                   locked_until = NULL,
                   last_error = 'Worker lease expired; provider outcome requires reconciliation.',
                   next_attempt_at = CAST(:now AS timestamptz) + make_interval(secs => LEAST(3600, (30 * power(2, LEAST(attempts - 1, 7)))::integer)),
                   updated_at = :now
             WHERE status IN ('PROCESSING', 'RECONCILING')
               AND locked_until <= :now
            """, nativeQuery = true)
    int moveExpiredProcessingToUnknown(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from PaymentGatewayOperationEntity operation where operation.id = :id")
    Optional<PaymentGatewayOperationEntity> findByIdForUpdate(@Param("id") UUID id);

    List<PaymentGatewayOperationEntity> findByLockedByOrderByCreatedAtAsc(String lockedBy);

    Optional<PaymentGatewayOperationEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentGatewayOperationEntity> findByPaymentIdAndOperationType(UUID paymentId,
                                                                              io.github.alighaziam.eom.payment.PaymentOperationType operationType);

    List<PaymentGatewayOperationEntity> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    long countByStatus(PaymentOperationStatus status);
}
