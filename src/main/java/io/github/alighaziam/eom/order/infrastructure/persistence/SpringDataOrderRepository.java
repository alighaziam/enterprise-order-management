package io.github.alighaziam.eom.order.infrastructure.persistence;
import jakarta.persistence.LockModeType;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
interface SpringDataOrderRepository extends JpaRepository<OrderEntity,UUID>{
 List<OrderEntity> findTop40ByOrderByCreatedAtDesc();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from OrderEntity o where o.id=:id")
 Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
 @Query("select distinct o from OrderEntity o left join fetch o.lines where o.id=:id")
 Optional<OrderEntity> findWithLinesById(@Param("id") UUID id);
 @Query("""
         select o from OrderEntity o
         where (:status is null or o.status = :status)
           and (:q is null or :q = '' or lower(str(o.id)) like lower(concat('%', :q, '%')))
         """)
 Page<OrderEntity> search(@Param("q") String q, @Param("status") OrderStatus status, Pageable pageable);
}
