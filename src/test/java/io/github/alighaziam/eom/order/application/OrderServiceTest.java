package io.github.alighaziam.eom.order.application;
import io.github.alighaziam.eom.catalog.*;
import io.github.alighaziam.eom.customer.CustomerOperations;
import io.github.alighaziam.eom.inventory.InventoryOperations;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderServiceTest {
 @Test void placesAndConfirmsOrderThroughPublishedModuleApis(){
  UUID customer=UUID.randomUUID(),product=UUID.randomUUID(),reservation=UUID.randomUUID();
  InMemoryStore store=new InMemoryStore();
  CustomerOperations customers=id->true;
  CatalogOperations catalog=id->Optional.of(new CatalogProduct(product,"SKU-1","Keyboard",new BigDecimal("10.00"),"EUR",true));
  InventoryOperations inventory=mock(InventoryOperations.class);
  when(inventory.reserve(product,2)).thenReturn(reservation);
  OrderService service=new OrderService(store,customers,catalog,inventory);
  var placed=service.place(customer,List.of(new OrderService.OrderRequestLine(product,2)));
  assertEquals(OrderStatus.RESERVED,placed.status());
  assertEquals(new BigDecimal("20.00"),placed.totalAmount());
  assertEquals(reservation,placed.lines().getFirst().reservationId());
  assertEquals(OrderStatus.CONFIRMED,service.confirm(placed.id()).status());
  assertEquals(OrderStatus.CONFIRMED,service.confirm(placed.id()).status());
  verify(inventory,times(1)).confirm(reservation);
 }
 @Test void cancellingOrderReleasesEachReservationOnce(){
  UUID product=UUID.randomUUID(),reservation=UUID.randomUUID();
  InMemoryStore store=new InMemoryStore();
  InventoryOperations inventory=mock(InventoryOperations.class);
  when(inventory.reserve(product,1)).thenReturn(reservation);
  CatalogOperations catalog=id->Optional.of(new CatalogProduct(product,"SKU-2","Mouse",new BigDecimal("5.00"),"EUR",true));
  OrderService service=new OrderService(store,id->true,catalog,inventory);
  var order=service.place(UUID.randomUUID(),List.of(new OrderService.OrderRequestLine(product,1)));
  assertEquals(OrderStatus.CANCELLED,service.cancel(order.id()).status());
  assertEquals(OrderStatus.CANCELLED,service.cancel(order.id()).status());
  verify(inventory,times(1)).release(reservation);
 }
 @Test void rejectsMixedCurrencyAndDuplicateProductLinesBeforePersisting(){
  UUID first=UUID.randomUUID(),second=UUID.randomUUID();
  InMemoryStore store=new InMemoryStore();
  CatalogOperations catalog=id->Optional.of(new CatalogProduct(id,"SKU","Product",BigDecimal.ONE,id.equals(first)?"EUR":"USD",true));
  OrderService service=new OrderService(store,id->true,catalog,mock(InventoryOperations.class));
  assertThrows(OrderConflictException.class,()->service.place(UUID.randomUUID(),List.of(new OrderService.OrderRequestLine(first,1),new OrderService.OrderRequestLine(second,1))));
  assertThrows(OrderConflictException.class,()->service.place(UUID.randomUUID(),List.of(new OrderService.OrderRequestLine(first,1),new OrderService.OrderRequestLine(first,2))));
  assertTrue(store.orders.isEmpty());
 }
 private static class InMemoryStore implements OrderStore{
  private final Map<UUID,io.github.alighaziam.eom.order.domain.Order> orders=new HashMap<>();
  public io.github.alighaziam.eom.order.domain.Order save(io.github.alighaziam.eom.order.domain.Order o){orders.put(o.id(),o);return o;}
  public Optional<io.github.alighaziam.eom.order.domain.Order> findById(UUID id){return Optional.ofNullable(orders.get(id));}
  public Optional<io.github.alighaziam.eom.order.domain.Order> findByIdForUpdate(UUID id){return findById(id);}
 }
}
