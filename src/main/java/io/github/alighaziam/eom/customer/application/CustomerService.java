package io.github.alighaziam.eom.customer.application;
import io.github.alighaziam.eom.customer.api.CustomerAlreadyExistsException;
import io.github.alighaziam.eom.customer.api.CustomerNotFoundException;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service @Transactional
public class CustomerService {
 private final CustomerStore customerStore;
 public CustomerService(CustomerStore customerStore) { this.customerStore = customerStore; }
 public Customer create(String fullName, String email) {
  String normalizedEmail = normalize(email);
  if (customerStore.existsByEmail(normalizedEmail)) throw new CustomerAlreadyExistsException(normalizedEmail);
  return customerStore.save(new Customer(UUID.randomUUID(), fullName.trim(), normalizedEmail, true, Instant.now()));
 }
 public Customer update(UUID id, String fullName, String email) {
  Customer current = required(id);
  String normalizedEmail = normalize(email);
  if (customerStore.emailUsedByOther(normalizedEmail, id)) throw new CustomerAlreadyExistsException(normalizedEmail);
  return customerStore.save(new Customer(current.id(), fullName.trim(), normalizedEmail, current.active(), current.createdAt()));
 }
 public Customer setActive(UUID id, boolean active) {
  Customer current = required(id);
  return customerStore.save(new Customer(current.id(), current.fullName(), current.email(), active, current.createdAt()));
 }
 @Transactional(readOnly = true)
 public Optional<Customer> findById(UUID id) { return customerStore.findById(id); }
 @Transactional(readOnly = true)
 public List<Customer> list() { return customerStore.findRecent(); }
 @Transactional(readOnly = true)
 public Page<Customer> search(String q, Boolean active, int page, int size) {
  return customerStore.search(blank(q), active, PageRequest.of(Math.max(page, 0), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
 }
 private Customer required(UUID id) { return customerStore.findById(id).orElseThrow(() -> new CustomerNotFoundException(id)); }
 private static String normalize(String email) { return email.trim().toLowerCase(); }
 private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static int clamp(int size) { return Math.min(Math.max(size, 1), 100); }
}