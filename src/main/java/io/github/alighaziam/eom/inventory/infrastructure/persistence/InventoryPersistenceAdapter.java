package io.github.alighaziam.eom.inventory.infrastructure.persistence;
import io.github.alighaziam.eom.inventory.application.InventoryStore;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public class InventoryPersistenceAdapter implements InventoryStore{
 private final SpringDataStockRepository repository;
 public InventoryPersistenceAdapter(SpringDataStockRepository repository){this.repository=repository;}
 public StockItem save(StockItem item){return toDomain(repository.save(new StockItemEntity(item.productId(),item.onHandQuantity(),item.reservedQuantity(),item.updatedAt())));}
 public Optional<StockItem> findByProductId(UUID id){return repository.findById(id).map(InventoryPersistenceAdapter::toDomain);}
 public boolean reserve(UUID id,int quantity){return repository.reserve(id,quantity)==1;}
 private static StockItem toDomain(StockItemEntity e){return new StockItem(e.getProductId(),e.getOnHandQuantity(),e.getReservedQuantity(),e.getUpdatedAt());}
}