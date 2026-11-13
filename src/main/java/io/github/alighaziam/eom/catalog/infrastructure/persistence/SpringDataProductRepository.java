package io.github.alighaziam.eom.catalog.infrastructure.persistence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
interface SpringDataProductRepository extends JpaRepository<ProductEntity,UUID>{
 boolean existsBySku(String sku);
 List<ProductEntity> findTop40ByOrderByCreatedAtDesc();
 @Query("""
         select p from ProductEntity p
         where (:q is null or :q = ''
             or lower(p.sku) like lower(concat('%', :q, '%'))
             or lower(p.name) like lower(concat('%', :q, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :q, '%')))
           and (:active is null or p.active = :active)
         """)
 Page<ProductEntity> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}