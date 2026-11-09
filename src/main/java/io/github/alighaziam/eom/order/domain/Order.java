package io.github.alighaziam.eom.order.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record Order(UUID id,UUID customerId,OrderStatus status,String currency,BigDecimal totalAmount,List<OrderLine> lines,Instant createdAt,Instant updatedAt){
 public Order{
  if(id==null||customerId==null||status==null||currency==null||totalAmount==null||lines==null||createdAt==null||updatedAt==null) throw new IllegalArgumentException("Order fields must not be null.");
  if(lines.isEmpty()) throw new IllegalArgumentException("An order must contain at least one line.");
  lines=List.copyOf(lines);
  if(totalAmount.signum()<=0) throw new IllegalArgumentException("Order total must be positive.");
 }
 public Order confirm(Instant now){
  if(status==OrderStatus.CONFIRMED)return this;
  if(status!=OrderStatus.RESERVED)throw new IllegalStateException("Only reserved orders can be confirmed.");
  return new Order(id,customerId,OrderStatus.CONFIRMED,currency,totalAmount,lines,createdAt,now);
 }
 public Order cancel(Instant now){
  if(status==OrderStatus.CANCELLED)return this;
  if(status!=OrderStatus.RESERVED)throw new IllegalStateException("Only reserved orders can be cancelled.");
  return new Order(id,customerId,OrderStatus.CANCELLED,currency,totalAmount,lines,createdAt,now);
 }
}
