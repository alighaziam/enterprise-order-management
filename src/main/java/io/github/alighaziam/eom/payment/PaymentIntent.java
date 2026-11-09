package io.github.alighaziam.eom.payment;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record PaymentIntent(UUID id,UUID orderId,BigDecimal amount,String currency,PaymentStatus status,String idempotencyKey,String providerReference,String failureReason,Instant createdAt,Instant updatedAt) {}