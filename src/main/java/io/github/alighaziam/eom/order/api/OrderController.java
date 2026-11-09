package io.github.alighaziam.eom.order.api;
import io.github.alighaziam.eom.order.application.*;
import io.github.alighaziam.eom.order.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/orders")
public class OrderController {
 private final OrderService service;
 public OrderController(OrderService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public OrderResponse place(@Valid @RequestBody PlaceOrderRequest r){
  return OrderResponse.from(service.place(r.customerId(),r.lines().stream().map(l->new OrderService.OrderRequestLine(l.productId(),l.quantity())).toList()));
 }
 @GetMapping("/{orderId}") public OrderResponse get(@PathVariable UUID orderId){return OrderResponse.from(service.get(orderId));}
 @PostMapping("/{orderId}/confirm") public OrderResponse confirm(@PathVariable UUID orderId){return OrderResponse.from(service.confirm(orderId));}
 @PostMapping("/{orderId}/cancel") public OrderResponse cancel(@PathVariable UUID orderId){return OrderResponse.from(service.cancel(orderId));}
 @ExceptionHandler(OrderNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(OrderNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler({OrderConflictException.class,IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.CONFLICT)
 ProblemDetail conflict(RuntimeException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());}
 public record PlaceOrderRequest(@NotNull UUID customerId,@NotEmpty List<@Valid OrderLineRequest> lines){}
 public record OrderLineRequest(@NotNull UUID productId,@Min(1) int quantity){}
 public record OrderLineResponse(UUID lineId,UUID productId,String sku,String productName,BigDecimal unitPrice,String currency,int quantity,UUID reservationId,BigDecimal subtotal){
  static OrderLineResponse from(OrderLine l){return new OrderLineResponse(l.id(),l.productId(),l.sku(),l.productName(),l.unitPrice(),l.currency(),l.quantity(),l.reservationId(),l.subtotal());}
 }
 public record OrderResponse(UUID orderId,UUID customerId,OrderStatus status,String currency,BigDecimal totalAmount,List<OrderLineResponse> lines,Instant createdAt,Instant updatedAt){
  static OrderResponse from(Order o){return new OrderResponse(o.id(),o.customerId(),o.status(),o.currency(),o.totalAmount(),o.lines().stream().map(OrderLineResponse::from).toList(),o.createdAt(),o.updatedAt());}
 }
}
