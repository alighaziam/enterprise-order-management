package io.github.alighaziam.eom.customer.infrastructure.persistence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataCustomerRepository extends JpaRepository<CustomerEntity, UUID> {
 boolean existsByEmail(String email);
 boolean existsByEmailAndIdNot(String email, UUID id);
 List<CustomerEntity> findTop40ByOrderByCreatedAtDesc();

 @Query("""
         select c from CustomerEntity c
         where (:q is null or :q = ''
             or lower(c.fullName) like lower(concat('%', :q, '%'))
             or lower(c.email) like lower(concat('%', :q, '%')))
           and (:active is null or c.active = :active)
         """)
 Page<CustomerEntity> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}