package io.github.alighaziam.eom.customer.infrastructure.persistence;
import io.github.alighaziam.eom.customer.application.CustomerStore;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public class CustomerPersistenceAdapter implements CustomerStore {
 private final SpringDataCustomerRepository repository;
 public CustomerPersistenceAdapter(SpringDataCustomerRepository repository) { this.repository = repository; }
 @Override public Customer save(Customer customer) {
  return toDomain(repository.save(new CustomerEntity(customer.id(), customer.fullName(), customer.email(), customer.createdAt())));
 }
 @Override public Optional<Customer> findById(UUID id) { return repository.findById(id).map(CustomerPersistenceAdapter::toDomain); }
 @Override public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
 private static Customer toDomain(CustomerEntity e) { return new Customer(e.getId(), e.getFullName(), e.getEmail(), e.getCreatedAt()); }
}