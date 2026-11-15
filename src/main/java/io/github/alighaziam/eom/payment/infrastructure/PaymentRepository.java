package io.github.alighaziam.eom.payment.infrastructure;

import io.github.alighaziam.eom.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentIntentEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO payment.payment_intents
                (id, order_id, amount, currency, status, idempotency_key, created_at, updated_at)
            VALUES
                (:id, :orderId, :amount, :currency, 'REQUESTED', :idempotencyKey, :now, :now)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("orderId") UUID orderId,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("now") Instant now);

    Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    List<PaymentIntentEntity> findTop40ByOrderByCreatedAtDesc();

    @Query("""
            select p from PaymentIntentEntity p
            where (:status is null or p.status = :status)
              and (:q is null or :q = '' or lower(str(p.id)) like lower(concat('%', :q, '%'))
                or lower(str(p.orderId)) like lower(concat('%', :q, '%')))
            """)
    Page<PaymentIntentEntity> search(@Param("q") String q,
                                     @Param("status") PaymentStatus status,
                                     Pageable pageable);
}
