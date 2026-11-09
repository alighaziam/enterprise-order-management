package io.github.alighaziam.eom.customer.application;

import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerStore customerStore;

    public CustomerService(CustomerStore customerStore) {
        this.customerStore = customerStore;
    }

    public Customer create(String fullName, String email) {
        Customer customer = new Customer(
                UUID.randomUUID(),
                fullName.trim(),
                email.trim().toLowerCase(),
                Instant.now()
        );
        return customerStore.save(customer);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findById(UUID id) {
        return customerStore.findById(id);
    }
}
