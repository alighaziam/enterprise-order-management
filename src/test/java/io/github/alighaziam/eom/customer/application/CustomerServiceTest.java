package io.github.alighaziam.eom.customer.application;

import io.github.alighaziam.eom.customer.domain.Customer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerServiceTest {

    @Test
    void createsCustomerWithNormalizedEmail() {
        CustomerStore store = new InMemoryCustomerStore();
        CustomerService service = new CustomerService(store);

        Customer customer = service.create(" Ada Lovelace ", " ADA@EXAMPLE.COM ");

        assertEquals("Ada Lovelace", customer.fullName());
        assertEquals("ada@example.com", customer.email());
        assertNotNull(customer.id());
        assertNotNull(customer.createdAt());
        assertEquals(Optional.of(customer), store.findById(customer.id()));
    }

    private static final class InMemoryCustomerStore implements CustomerStore {
        private Customer saved;

        @Override
        public Customer save(Customer customer) {
            saved = customer;
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID id) {
            return saved != null && saved.id().equals(id) ? Optional.of(saved) : Optional.empty();
        }
    }
}
