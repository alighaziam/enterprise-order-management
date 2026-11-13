package io.github.alighaziam.eom.order;
import java.math.BigDecimal;import java.util.Collection;import java.util.UUID;
public interface OrderOperations {
 OrderSummary placeForCheckout(UUID customerId,Collection<OrderLineRequest> lines);
 OrderSummary confirmForCheckout(UUID orderId);OrderSummary cancelForCheckout(UUID orderId);OrderState state(UUID orderId);
 record OrderLineRequest(UUID productId,int quantity){}
 record OrderSummary(UUID id,BigDecimal totalAmount,String currency){}
}