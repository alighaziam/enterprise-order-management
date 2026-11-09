package io.github.alighaziam.eom.inventory.application;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InventoryServiceTest{
 @Test void reservesOnlyAvailableQuantity(){
  InMemoryStore store=new InMemoryStore();InventoryService service=new InventoryService(store);UUID id=UUID.randomUUID();
  service.initializeStock(id,10);StockItem reserved=service.reserve(id,7);
  assertEquals(7,reserved.reservedQuantity());assertEquals(3,reserved.availableQuantity());
  assertThrows(InsufficientStockException.class,()->service.reserve(id,4));
 }
 @Test void rejectsNonPositiveReservation(){
  InventoryService service=new InventoryService(new InMemoryStore());
  assertThrows(IllegalArgumentException.class,()->service.reserve(UUID.randomUUID(),0));
 }
 private static class InMemoryStore implements InventoryStore{
  private final Map<UUID,StockItem> data=new HashMap<>();
  public StockItem save(StockItem item){data.put(item.productId(),item);return item;}
  public Optional<StockItem> findByProductId(UUID id){return Optional.ofNullable(data.get(id));}
  public boolean reserve(UUID id,int qty){
   StockItem current=data.get(id);if(current==null||current.availableQuantity()<qty)return false;
   data.put(id,new StockItem(id,current.onHandQuantity(),current.reservedQuantity()+qty,java.time.Instant.now()));return true;
  }
 }
}