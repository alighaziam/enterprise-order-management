package io.github.alighaziam.eom.payment.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface PaymentRepository extends JpaRepository<PaymentIntentEntity,UUID>{Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);}