package io.github.alighaziam.eom.customer.infrastructure.persistence;
import io.github.alighaziam.eom.customer.application.CustomerStore;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public class CustomerPersistenceAdapter implements CustomerStore {
 private final SpringDataCustomerRepository repository;
 public CustomerPersistenceAdapter(SpringDataCustomerRepository repository) { this.repository = repository; }
 @Override public Customer save(Customer customer) {
  return toDomain(repository.save(new CustomerEntity(customer.id(), customer.fullName(), customer.email(), customer.active(), customer.createdAt())));
 }
 @Override public Optional<Customer> findById(UUID id) { return repository.findById(id).map(CustomerPersistenceAdapter::toDomain); }
 @Override public List<Customer> findRecent() { return repository.findTop40ByOrderByCreatedAtDesc().stream().map(CustomerPersistenceAdapter::toDomain).toList(); }
 @Override public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
 @Override public boolean emailUsedByOther(String email, UUID id) { return repository.existsByEmailAndIdNot(email, id); }
 @Override public Page<Customer> search(String q, Boolean active, Pageable pageable) { return repository.search(q, active, pageable).map(CustomerPersistenceAdapter::toDomain); }
 private static Customer toDomain(CustomerEntity e) { return new Customer(e.getId(), e.getFullName(), e.getEmail(), e.isActive(), e.getCreatedAt()); }
}