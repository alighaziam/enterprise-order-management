package io.github.alighaziam.eom.customer.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
interface SpringDataCustomerRepository extends JpaRepository<CustomerEntity, UUID> {
 boolean existsByEmail(String email);
}