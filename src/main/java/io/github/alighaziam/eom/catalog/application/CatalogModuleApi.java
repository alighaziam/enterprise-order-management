package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.CatalogOperations;
import io.github.alighaziam.eom.catalog.CatalogProduct;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;
@Component
public class CatalogModuleApi implements CatalogOperations {
 private final ProductService service;
 public CatalogModuleApi(ProductService service){this.service=service;}
 public Optional<CatalogProduct> findActiveProduct(UUID id){
  return service.findById(id).filter(Product::active).map(p->new CatalogProduct(p.id(),p.sku(),p.name(),p.unitPrice(),p.currency(),p.active()));
 }
}
