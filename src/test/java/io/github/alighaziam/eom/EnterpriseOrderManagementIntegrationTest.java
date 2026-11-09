package io.github.alighaziam.eom;
import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.catalog.domain.Product;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import io.github.alighaziam.eom.order.application.OrderService;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class EnterpriseOrderManagementIntegrationTest {
 @Autowired private CustomerService customerService;
 @Autowired private ProductService productService;
 @Autowired private InventoryService inventoryService;
 @Autowired private OrderService orderService;

 @Test void persistsCustomerProductAndStockAcrossModules(){
  String unique=UUID.randomUUID().toString();
  var customer=customerService.create("Integration Customer","customer-"+unique+"@example.com");
  assertNotNull(customerService.findById(customer.id()).orElseThrow());
  Product product=productService.create("SKU-"+unique.substring(0,8),"Integration Product","Created by integration test",new BigDecimal("29.9900"),"USD");
  assertNotNull(productService.findById(product.id()).orElseThrow());
  inventoryService.initializeStock(product.id(),8);
  Reservation reservation=inventoryService.reserve(product.id(),3);
  assertEquals(ReservationStatus.PENDING,reservation.status());
  StockItem reserved=inventoryService.find(product.id());
  assertEquals(8,reserved.onHandQuantity());assertEquals(3,reserved.reservedQuantity());assertEquals(5,reserved.availableQuantity());
  Reservation confirmed=inventoryService.confirm(reservation.id());
  assertEquals(ReservationStatus.CONFIRMED,confirmed.status());
  StockItem consumed=inventoryService.find(product.id());
  assertEquals(5,consumed.onHandQuantity());assertEquals(0,consumed.reservedQuantity());assertEquals(5,consumed.availableQuantity());
 }

 @Test void releasingReservationRestoresAvailability(){
  String unique=UUID.randomUUID().toString();
  Product product=productService.create("REL-"+unique.substring(0,8),"Release Test Product","Reservation release integration test",new BigDecimal("10.00"),"USD");
  inventoryService.initializeStock(product.id(),6);
  Reservation reservation=inventoryService.reserve(product.id(),4);
  assertEquals(ReservationStatus.RELEASED,inventoryService.release(reservation.id()).status());
  assertEquals(ReservationStatus.RELEASED,inventoryService.release(reservation.id()).status());
  StockItem stock=inventoryService.find(product.id());
  assertEquals(6,stock.onHandQuantity());assertEquals(0,stock.reservedQuantity());assertEquals(6,stock.availableQuantity());
 }

 @Test void placesConfirmsAndCancelsOrdersWithInventoryConsistency(){
  String unique=UUID.randomUUID().toString();
  var customer=customerService.create("Order Customer","order-"+unique+"@example.com");
  Product product=productService.create("ORD-"+unique.substring(0,8),"Order Product","Order integration test",new BigDecimal("15.00"),"EUR");
  inventoryService.initializeStock(product.id(),10);
  var placed=orderService.place(customer.id(),java.util.List.of(new OrderService.OrderRequestLine(product.id(),3)));
  assertEquals(OrderStatus.RESERVED,placed.status());
  assertEquals(new BigDecimal("45.00"),placed.totalAmount());
  assertEquals(7,inventoryService.find(product.id()).availableQuantity());
  assertEquals(OrderStatus.CONFIRMED,orderService.confirm(placed.id()).status());
  assertEquals(7,inventoryService.find(product.id()).onHandQuantity());
  assertEquals(OrderStatus.CONFIRMED,orderService.confirm(placed.id()).status());

  Product cancelProduct=productService.create("CAN-"+unique.substring(0,8),"Cancel Product","Order cancellation test",new BigDecimal("7.00"),"EUR");
  inventoryService.initializeStock(cancelProduct.id(),5);
  var cancelOrder=orderService.place(customer.id(),java.util.List.of(new OrderService.OrderRequestLine(cancelProduct.id(),2)));
  assertEquals(3,inventoryService.find(cancelProduct.id()).availableQuantity());
  assertEquals(OrderStatus.CANCELLED,orderService.cancel(cancelOrder.id()).status());
  assertEquals(OrderStatus.CANCELLED,orderService.cancel(cancelOrder.id()).status());
  assertEquals(5,inventoryService.find(cancelProduct.id()).availableQuantity());
  assertEquals(OrderStatus.CANCELLED,orderService.get(cancelOrder.id()).status());
 }
}
