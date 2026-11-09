package io.github.alighaziam.eom.order.infrastructure.persistence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
interface SpringDataOrderRepository extends JpaRepository<OrderEntity,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from OrderEntity o where o.id=:id")
 Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
 @Query("select distinct o from OrderEntity o left join fetch o.lines where o.id=:id")
 Optional<OrderEntity> findWithLinesById(@Param("id") UUID id);
}
