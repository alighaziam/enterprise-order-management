package io.github.alighaziam.eom.customer.application;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CustomerStore {
 Customer save(Customer customer);
 Optional<Customer> findById(UUID id);
 List<Customer> findRecent();
 boolean existsByEmail(String normalizedEmail);
 boolean emailUsedByOther(String normalizedEmail, UUID id);
 Page<Customer> search(String q, Boolean active, Pageable pageable);
}