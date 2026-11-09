package io.github.alighaziam.eom.inventory.application;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
@Service @Transactional
public class InventoryService{
 private final InventoryStore store;
 public InventoryService(InventoryStore store){this.store=store;}
 public StockItem initializeStock(UUID id,int quantity){
  if(quantity<0)throw new IllegalArgumentException("Initial stock cannot be negative.");
  if(store.findByProductId(id).isPresent())throw new IllegalStateException("Stock has already been initialized for product: "+id);
  return store.save(new StockItem(id,quantity,0,Instant.now()));
 }
 public StockItem reserve(UUID id,int quantity){
  if(quantity<=0)throw new IllegalArgumentException("Reservation quantity must be positive.");
  if(store.findByProductId(id).isEmpty())throw new StockItemNotFoundException(id);
  if(!store.reserve(id,quantity))throw new InsufficientStockException(id,quantity);
  return store.findByProductId(id).orElseThrow();
 }
 @Transactional(readOnly=true) public StockItem find(UUID id){return store.findByProductId(id).orElseThrow(()->new StockItemNotFoundException(id));}
}