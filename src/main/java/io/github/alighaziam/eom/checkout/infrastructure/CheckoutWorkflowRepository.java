package io.github.alighaziam.eom.checkout.infrastructure;
import io.github.alighaziam.eom.checkout.CheckoutStatus;import org.springframework.data.jpa.repository.JpaRepository;import java.time.Instant;import java.util.List;import java.util.Optional;import java.util.UUID;
public interface CheckoutWorkflowRepository extends JpaRepository<CheckoutWorkflowEntity,UUID>{
 Optional<CheckoutWorkflowEntity> findByIdempotencyKey(String key);
 List<CheckoutWorkflowEntity> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByUpdatedAtAsc(CheckoutStatus status,Instant now);
}