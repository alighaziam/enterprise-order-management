package io.github.alighaziam.eom.customer.application;
import io.github.alighaziam.eom.customer.api.CustomerAlreadyExistsException;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.junit.jupiter.api.Test;
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
  public boolean existsByEmail(String email){return customers.values().stream().anyMatch(c->c.email().equals(email));}
 }
}