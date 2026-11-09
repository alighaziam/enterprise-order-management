package io.github.alighaziam.eom.customer.infrastructure.persistence;

import io.github.alighaziam.eom.customer.application.CustomerStore;
import io.github.alighaziam.eom.customer.domain.Customer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerPersistenceAdapter implements CustomerStore {

    private final SpringDataCustomerRepository repository;

    public CustomerPersistenceAdapter(SpringDataCustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = new CustomerEntity(
                customer.id(), customer.fullName(), customer.email(), customer.createdAt()
        );
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return repository.findById(id).map(CustomerPersistenceAdapter::toDomain);
    }

    private static Customer toDomain(CustomerEntity entity) {
        return new Customer(entity.getId(), entity.getFullName(), entity.getEmail(), entity.getCreatedAt());
    }
}
