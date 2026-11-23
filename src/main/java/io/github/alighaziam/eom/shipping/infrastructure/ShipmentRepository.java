package io.github.alighaziam.eom.shipping.infrastructure;

import io.github.alighaziam.eom.shipping.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<ShipmentEntity, UUID> {

    Optional<ShipmentEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShipmentEntity s where s.id = :id")
    Optional<ShipmentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            WITH candidates AS (
                SELECT id
                FROM shipping.shipments
                WHERE status = 'PENDING'
                  AND carrier_reference IS NULL
                  AND label_next_attempt_at <= :now
                  AND (label_locked_until IS NULL OR label_locked_until <= :now)
                ORDER BY label_next_attempt_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE shipping.shipments AS shipment
               SET label_locked_by = :workerId,
                   label_locked_until = :leaseUntil,
                   label_attempts = shipment.label_attempts + 1,
                   updated_at = :now
              FROM candidates
             WHERE shipment.id = candidates.id
            """, nativeQuery = true)
    int claimPendingLabels(@Param("workerId") String workerId,
                           @Param("now") java.time.Instant now,
                           @Param("leaseUntil") java.time.Instant leaseUntil,
                           @Param("batchSize") int batchSize);

    List<ShipmentEntity> findByLabelLockedByOrderByCreatedAtAsc(String labelLockedBy);

    List<ShipmentEntity> findTop40ByOrderByCreatedAtDesc();

    @Query("""
            select s from ShipmentEntity s
            where (:status is null or s.status = :status)
              and (:q is null or :q = '' or lower(s.recipientName) like lower(concat('%', :q, '%'))
                or lower(s.city) like lower(concat('%', :q, '%'))
                or lower(str(s.id)) like lower(concat('%', :q, '%'))
                or lower(str(s.orderId)) like lower(concat('%', :q, '%')))
            """)
    Page<ShipmentEntity> search(@Param("q") String q,
                                @Param("status") ShipmentStatus status,
                                Pageable pageable);
}
