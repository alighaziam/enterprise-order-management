package io.github.alighaziam.eom.checkout.infrastructure;
import io.github.alighaziam.eom.checkout.CheckoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface CheckoutWorkflowRepository extends JpaRepository<CheckoutWorkflowEntity,UUID>{
 Optional<CheckoutWorkflowEntity> findByIdempotencyKey(String key);
 List<CheckoutWorkflowEntity> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByUpdatedAtAsc(CheckoutStatus status,Instant now);
 List<CheckoutWorkflowEntity> findTop40ByOrderByUpdatedAtDesc();
 long countByStatus(CheckoutStatus status);
 @Query("""
         select w from CheckoutWorkflowEntity w
         where (:status is null or w.status = :status)
           and (:q is null or :q = '' or lower(str(w.id)) like lower(concat('%', :q, '%'))
             or lower(coalesce(w.lastError, '')) like lower(concat('%', :q, '%')))
         """)
 Page<CheckoutWorkflowEntity> search(@Param("q") String q, @Param("status") CheckoutStatus status, Pageable pageable);
}