package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProductStore {
 Product save(Product product);
 Optional<Product> findById(UUID id);
 List<Product> findRecent();
 boolean existsBySku(String sku);
 Page<Product> search(String q, Boolean active, Pageable pageable);
}