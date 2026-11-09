package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductServiceTest {
 @Test void normalizesSkuAndCurrency(){
  InMemoryStore store=new InMemoryStore(); Product p=new ProductService(store).create(" sku-1 ","Keyboard",null,new BigDecimal("19.9900"),"eur");
  assertEquals("SKU-1",p.sku());assertEquals("EUR",p.currency());assertEquals(new BigDecimal("19.9900"),p.unitPrice());
 }
 @Test void rejectsDuplicateSku(){
  InMemoryStore store=new InMemoryStore();ProductService service=new ProductService(store);
  service.create("SKU-1","Keyboard",null,new BigDecimal("10.00"),"USD");
  assertThrows(DuplicateSkuException.class,()->service.create("sku-1","Other",null,new BigDecimal("12.00"),"USD"));
 }
 private static class InMemoryStore implements ProductStore{
  private final Map<UUID,Product> data=new HashMap<>();
  public Product save(Product p){data.put(p.id(),p);return p;}
  public Optional<Product> findById(UUID id){return Optional.ofNullable(data.get(id));}
  public boolean existsBySku(String sku){return data.values().stream().anyMatch(p->p.sku().equals(sku));}
 }
}