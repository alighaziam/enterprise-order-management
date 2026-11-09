package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.domain.Product;
import java.util.Optional;
import java.util.UUID;
public interface ProductStore {
 Product save(Product product);
 Optional<Product> findById(UUID id);
 boolean existsBySku(String sku);
}