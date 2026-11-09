package io.github.alighaziam.eom.order.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OrderTest {
 private Order sample(){
  Instant now=Instant.now();
  OrderLine line=new OrderLine(UUID.randomUUID(),UUID.randomUUID(),"SKU-1","Product",new BigDecimal("12.50"),"EUR",2,UUID.randomUUID());
  return new Order(UUID.randomUUID(),UUID.randomUUID(),OrderStatus.RESERVED,"EUR",new BigDecimal("25.00"),List.of(line),now,now);
 }
 @Test void confirmsOnlyReservedOrders(){
  Order order=sample();
  assertEquals(OrderStatus.CONFIRMED,order.confirm(Instant.now()).status());
  assertThrows(IllegalStateException.class,()->order.cancel(Instant.now()).confirm(Instant.now()));
 }
 @Test void cancellationIsIdempotentAndConfirmedOrderCannotBeCancelled(){
  Order order=sample();
  Order cancelled=order.cancel(Instant.now());
  assertEquals(OrderStatus.CANCELLED,cancelled.cancel(Instant.now()).status());
  assertThrows(IllegalStateException.class,()->order.confirm(Instant.now()).cancel(Instant.now()));
 }
 @Test void rejectsEmptyOrder(){
  Instant now=Instant.now();
  assertThrows(IllegalArgumentException.class,()->new Order(UUID.randomUUID(),UUID.randomUUID(),OrderStatus.RESERVED,"EUR",BigDecimal.ONE,List.of(),now,now));
 }
}
