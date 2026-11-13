package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
@Service @Transactional
public class ProductService {
 private final ProductStore store;
 public ProductService(ProductStore store){this.store=store;}
 public Product create(String sku,String name,String description,BigDecimal price,String currency){
  String normalizedSku=sku.trim().toUpperCase(Locale.ROOT);
  if(store.existsBySku(normalizedSku)) throw new DuplicateSkuException(normalizedSku);
  if(price==null||price.signum()<=0) throw new IllegalArgumentException("Unit price must be greater than zero.");
  String normalizedCurrency=currency.trim().toUpperCase(Locale.ROOT);
  if(!normalizedCurrency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code.");
  return store.save(new Product(UUID.randomUUID(),normalizedSku,name.trim(),description==null?null:description.trim(),
   price,normalizedCurrency,true,Instant.now()));
 }
 @Transactional(readOnly=true) public Optional<Product> findById(UUID id){return store.findById(id);}
 @Transactional(readOnly=true) public List<Product> list(){return store.findRecent();}
 public Product update(UUID id, String name, String description, BigDecimal price, String currency){
  Product current=store.findById(id).orElseThrow(()->new ProductNotFoundException(id));
  if(price==null||price.signum()<=0) throw new IllegalArgumentException("Unit price must be greater than zero.");
  String normalizedCurrency=currency.trim().toUpperCase(Locale.ROOT);
  if(!normalizedCurrency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code.");
  return store.save(new Product(current.id(), current.sku(), name.trim(), description==null?null:description.trim(), price, normalizedCurrency, current.active(), current.createdAt()));
 }
 public Product setActive(UUID id, boolean active){
  Product current=store.findById(id).orElseThrow(()->new ProductNotFoundException(id));
  return store.save(new Product(current.id(), current.sku(), current.name(), current.description(), current.unitPrice(), current.currency(), active, current.createdAt()));
 }
 @Transactional(readOnly=true) public Page<Product> search(String q, Boolean active, int page, int size){
  return store.search(q==null||q.isBlank()?null:q.trim(), active, PageRequest.of(Math.max(page,0), Math.min(Math.max(size,1),100), Sort.by(Sort.Direction.DESC,"createdAt")));
 }
}