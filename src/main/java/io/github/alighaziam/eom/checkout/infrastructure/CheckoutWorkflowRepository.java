package io.github.alighaziam.eom.checkout.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;import java.util.UUID;
public interface CheckoutWorkflowRepository extends JpaRepository<CheckoutWorkflowEntity,UUID>{Optional<CheckoutWorkflowEntity> findByIdempotencyKey(String key);}