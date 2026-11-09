package io.github.alighaziam.eom.order;
import io.github.alighaziam.eom.order.domain.Order; import java.util.Collection; import java.util.UUID;
public interface OrderOperations {Order place(UUID customerId,Collection<OrderLineRequest> lines);Order confirm(UUID id);Order cancel(UUID id);Order get(UUID id);record OrderLineRequest(UUID productId,int quantity){}}