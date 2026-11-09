package io.github.alighaziam.eom.catalog.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
interface SpringDataProductRepository extends JpaRepository<ProductEntity,UUID>{boolean existsBySku(String sku);}