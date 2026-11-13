package io.github.alighaziam.eom.shipping.infrastructure;
import io.github.alighaziam.eom.shipping.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List; import java.util.Optional; import java.util.UUID;
public interface ShipmentRepository extends JpaRepository<ShipmentEntity,UUID>{
 Optional<ShipmentEntity> findByOrderId(UUID orderId);
 List<ShipmentEntity> findTop40ByOrderByCreatedAtDesc();
 @Query("""
         select s from ShipmentEntity s
         where (:status is null or s.status = :status)
           and (:q is null or :q = '' or lower(s.recipientName) like lower(concat('%', :q, '%'))
             or lower(s.city) like lower(concat('%', :q, '%'))
             or lower(str(s.id)) like lower(concat('%', :q, '%')))
         """)
 Page<ShipmentEntity> search(@Param("q") String q, @Param("status") ShipmentStatus status, Pageable pageable);
}