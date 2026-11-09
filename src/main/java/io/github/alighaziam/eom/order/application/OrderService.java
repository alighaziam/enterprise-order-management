package io.github.alighaziam.eom.order.application;
import io.github.alighaziam.eom.catalog.CatalogOperations;
import io.github.alighaziam.eom.catalog.CatalogProduct;
import io.github.alighaziam.eom.customer.CustomerOperations;
import io.github.alighaziam.eom.inventory.InventoryOperations;
import io.github.alighaziam.eom.order.domain.Order;
import io.github.alighaziam.eom.order.domain.OrderLine;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service @Transactional
public class OrderService {
 private final OrderStore store;
 private final CustomerOperations customers;
 private final CatalogOperations catalog;
 private final InventoryOperations inventory;
 public OrderService(OrderStore store,CustomerOperations customers,CatalogOperations catalog,InventoryOperations inventory){
  this.store=store;this.customers=customers;this.catalog=catalog;this.inventory=inventory;
 }
 public Order place(UUID customerId,List<OrderRequestLine> requested){
  if(customerId==null)throw new OrderConflictException("Customer ID is required.");
  if(requested==null||requested.isEmpty())throw new OrderConflictException("An order must contain at least one line.");
  if(!customers.customerExists(customerId))throw new OrderConflictException("Customer does not exist: "+customerId);
  Set<UUID> seen=new HashSet<>();
  List<OrderLine> lines=new ArrayList<>();
  String currency=null;
  BigDecimal total=BigDecimal.ZERO;
  for(OrderRequestLine input:requested){
   if(input==null||input.productId()==null||input.quantity()<=0)throw new OrderConflictException("Each order line requires a product and a positive quantity.");
   if(!seen.add(input.productId()))throw new OrderConflictException("Duplicate product lines are not allowed: "+input.productId());
   CatalogProduct product=catalog.findActiveProduct(input.productId()).orElseThrow(()->new OrderConflictException("Product does not exist or is inactive: "+input.productId()));
   if(currency==null)currency=product.currency();
   if(!currency.equals(product.currency()))throw new OrderConflictException("All order lines must use the same currency.");
   UUID reservationId;
   try{reservationId=inventory.reserve(product.id(),input.quantity());}
   catch(RuntimeException ex){throw new OrderConflictException("Unable to reserve stock for product "+product.sku()+".",ex);}
   OrderLine line=new OrderLine(UUID.randomUUID(),product.id(),product.sku(),product.name(),product.unitPrice(),product.currency(),input.quantity(),reservationId);
   lines.add(line);total=total.add(line.subtotal());
  }
  Instant now=Instant.now();
  return store.save(new Order(UUID.randomUUID(),customerId,OrderStatus.RESERVED,currency,total,lines,now,now));
 }
 public Order confirm(UUID id){
  Order order=findForUpdate(id);
  if(order.status()==OrderStatus.CONFIRMED)return order;
  if(order.status()!=OrderStatus.RESERVED)throw new OrderConflictException("Only reserved orders can be confirmed.");
  try{for(OrderLine line:order.lines())inventory.confirm(line.reservationId());}
  catch(RuntimeException ex){throw new OrderConflictException("Order confirmation failed; no partial confirmation was committed.",ex);}
  return store.save(order.confirm(Instant.now()));
 }
 public Order cancel(UUID id){
  Order order=findForUpdate(id);
  if(order.status()==OrderStatus.CANCELLED)return order;
  if(order.status()!=OrderStatus.RESERVED)throw new OrderConflictException("Only reserved orders can be cancelled.");
  try{for(OrderLine line:order.lines())inventory.release(line.reservationId());}
  catch(RuntimeException ex){throw new OrderConflictException("Order cancellation failed; no partial release was committed.",ex);}
  return store.save(order.cancel(Instant.now()));
 }
 @Transactional(readOnly=true) public Order get(UUID id){return store.findById(id).orElseThrow(()->new OrderNotFoundException(id));}
 private Order findForUpdate(UUID id){return store.findByIdForUpdate(id).orElseThrow(()->new OrderNotFoundException(id));}
 public record OrderRequestLine(UUID productId,int quantity){}
}
