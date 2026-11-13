package io.github.alighaziam.eom.payment.infrastructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List; import java.util.Optional; import java.util.UUID;
public interface PaymentRepository extends JpaRepository<PaymentIntentEntity,UUID>{
 Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);
 List<PaymentIntentEntity> findTop40ByOrderByCreatedAtDesc();
 @Query("""
         select p from PaymentIntentEntity p
         where (:status is null or p.status = :status)
           and (:q is null or :q = '' or lower(str(p.id)) like lower(concat('%', :q, '%'))
             or lower(str(p.orderId)) like lower(concat('%', :q, '%')))
         """)
 Page<PaymentIntentEntity> search(@Param("q") String q, @Param("status") io.github.alighaziam.eom.payment.PaymentStatus status, Pageable pageable);
}