package io.github.alighaziam.eom.catalog.application;
import io.github.alighaziam.eom.catalog.domain.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
  public List<Product> findRecent(){return data.values().stream().sorted(Comparator.comparing(Product::createdAt).reversed()).limit(40).toList();}
  public boolean existsBySku(String sku){return data.values().stream().anyMatch(p->p.sku().equals(sku));}
  public Page<Product> search(String q, Boolean active, Pageable pageable){
   String needle=q==null?"":q.toLowerCase();
   var matched=data.values().stream().filter(p->active==null||p.active()==active).filter(p->needle.isEmpty()||p.sku().toLowerCase().contains(needle)||p.name().toLowerCase().contains(needle)).sorted(Comparator.comparing(Product::createdAt).reversed()).toList();
   int start=(int)Math.min(pageable.getOffset(), matched.size());
   return new PageImpl<>(matched.subList(start, Math.min(start+pageable.getPageSize(), matched.size())), pageable, matched.size());
  }
 }
}