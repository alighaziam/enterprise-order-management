package io.github.alighaziam.eom.catalog.infrastructure.persistence;
import io.github.alighaziam.eom.catalog.application.ProductStore;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public class ProductPersistenceAdapter implements ProductStore {
 private final SpringDataProductRepository repository;
 public ProductPersistenceAdapter(SpringDataProductRepository repository){this.repository=repository;}
 public Product save(Product p){return toDomain(repository.save(new ProductEntity(p.id(),p.sku(),p.name(),p.description(),p.unitPrice(),p.currency(),p.active(),p.createdAt())));}
 public Optional<Product> findById(UUID id){return repository.findById(id).map(ProductPersistenceAdapter::toDomain);}
 public boolean existsBySku(String sku){return repository.existsBySku(sku);}
 private static Product toDomain(ProductEntity e){return new Product(e.getId(),e.getSku(),e.getName(),e.getDescription(),e.getUnitPrice(),e.getCurrency(),e.isActive(),e.getCreatedAt());}
}