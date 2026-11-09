package io.github.alighaziam.eom.checkout;
import io.github.alighaziam.eom.order.OrderOperations;import io.github.alighaziam.eom.shipping.DeliveryAddress;import java.util.List;import java.util.UUID;
public interface CheckoutOperations {
 CheckoutResult checkout(CheckoutRequest request);CheckoutResult get(UUID workflowId);
 record CheckoutRequest(UUID customerId,List<OrderOperations.OrderLineRequest> lines,DeliveryAddress deliveryAddress,String idempotencyKey){}
 record CheckoutResult(UUID workflowId,UUID orderId,UUID paymentId,UUID shipmentId,CheckoutStatus status,String lastError){}
}