package io.github.alighaziam.eom.payment;
import java.math.BigDecimal; import java.util.UUID;
public interface PaymentOperations {
 PaymentIntent authorize(UUID orderId,BigDecimal amount,String currency,String idempotencyKey);
 PaymentIntent capture(UUID paymentId); PaymentIntent voidAuthorization(UUID paymentId); PaymentIntent refund(UUID paymentId); PaymentIntent get(UUID paymentId);
}