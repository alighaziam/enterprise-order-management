package io.github.alighaziam.eom.customer.application;

import io.github.alighaziam.eom.customer.domain.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerStore {
    Customer save(Customer customer);
    Optional<Customer> findById(UUID id);
}
