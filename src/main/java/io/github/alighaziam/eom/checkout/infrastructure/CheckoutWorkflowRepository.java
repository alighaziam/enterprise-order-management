package io.github.alighaziam.eom.checkout.infrastructure;

import io.github.alighaziam.eom.checkout.CheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutWorkflowRepository extends JpaRepository<CheckoutWorkflowEntity, UUID> {

    Optional<CheckoutWorkflowEntity> findByIdempotencyKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from CheckoutWorkflowEntity w where w.id = :id")
    Optional<CheckoutWorkflowEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            WITH due AS (
                SELECT id
                FROM checkout.workflows
                WHERE (
                    (status = 'FULFILLMENT_PENDING' AND next_attempt_at <= :now)
                    OR (status = 'ORDER_CONFIRMED' AND updated_at <= :recoveryCutoff)
                )
                  AND (fulfillment_lease_until IS NULL OR fulfillment_lease_until < :now)
                ORDER BY COALESCE(next_attempt_at, updated_at) ASC, updated_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE checkout.workflows AS workflow
               SET fulfillment_locked_by = :workerId,
                   fulfillment_lease_until = :leaseUntil,
                   updated_at = :now
              FROM due
             WHERE workflow.id = due.id
            """, nativeQuery = true)
    int claimDueFulfillment(@Param("workerId") String workerId,
                            @Param("now") Instant now,
                            @Param("leaseUntil") Instant leaseUntil,
                            @Param("recoveryCutoff") Instant recoveryCutoff,
                            @Param("batchSize") int batchSize);

    List<CheckoutWorkflowEntity> findByFulfillmentLockedByOrderByNextAttemptAtAsc(String workerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CheckoutWorkflowEntity> findByIdAndFulfillmentLockedByAndFulfillmentLeaseUntilAfter(
            UUID id, String workerId, Instant now);

    List<CheckoutWorkflowEntity> findTop40ByOrderByUpdatedAtDesc();

    long countByStatus(CheckoutStatus status);

    @Query("""
            select w from CheckoutWorkflowEntity w
            where (:status is null or w.status = :status)
              and (:q is null or :q = '' or lower(str(w.id)) like lower(concat('%', :q, '%'))
                or lower(coalesce(w.lastError, '')) like lower(concat('%', :q, '%')))
            """)
    Page<CheckoutWorkflowEntity> search(@Param("q") String q,
                                        @Param("status") CheckoutStatus status,
                                        Pageable pageable);
}
