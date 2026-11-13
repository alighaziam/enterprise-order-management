package io.github.alighaziam.eom.customer.application;
import io.github.alighaziam.eom.customer.api.CustomerAlreadyExistsException;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CustomerServiceTest {
 @Test void createsCustomerWithNormalizedEmail() {
  InMemoryCustomerStore store=new InMemoryCustomerStore(); CustomerService service=new CustomerService(store);
  Customer c=service.create(" Ada Lovelace "," ADA@EXAMPLE.COM ");
  assertEquals("Ada Lovelace",c.fullName()); assertEquals("ada@example.com",c.email());
  assertNotNull(c.id()); assertNotNull(c.createdAt()); assertEquals(Optional.of(c),store.findById(c.id()));
 }
 @Test void rejectsDuplicateEmail() {
  InMemoryCustomerStore store=new InMemoryCustomerStore(); CustomerService service=new CustomerService(store);
  service.create("Ada Lovelace","ada@example.com");
  assertThrows(CustomerAlreadyExistsException.class,()->service.create("Another Person"," ADA@example.com "));
 }
 private static final class InMemoryCustomerStore implements CustomerStore {
  private final Map<UUID,Customer> customers=new HashMap<>();
  public Customer save(Customer c){customers.put(c.id(),c);return c;}
  public Optional<Customer> findById(UUID id){return Optional.ofNullable(customers.get(id));}
  public java.util.List<Customer> findRecent(){return customers.values().stream().sorted(java.util.Comparator.comparing(Customer::createdAt).reversed()).limit(40).toList();}
  public boolean existsByEmail(String email){return customers.values().stream().anyMatch(c->c.email().equals(email));}
  public boolean emailUsedByOther(String email, UUID id){return customers.values().stream().anyMatch(c->c.email().equals(email)&&!c.id().equals(id));}
  public Page<Customer> search(String q, Boolean active, Pageable pageable){
   String needle=q==null?"":q.toLowerCase();
   var matched=customers.values().stream().filter(c->active==null||c.active()==active).filter(c->needle.isEmpty()||c.fullName().toLowerCase().contains(needle)||c.email().contains(needle)).sorted(java.util.Comparator.comparing(Customer::createdAt).reversed()).toList();
   int start=(int)Math.min(pageable.getOffset(), matched.size());
   return new PageImpl<>(matched.subList(start, Math.min(start+pageable.getPageSize(), matched.size())), pageable, matched.size());
  }
 }
}