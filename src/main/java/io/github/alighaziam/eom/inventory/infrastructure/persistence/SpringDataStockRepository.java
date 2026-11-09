package io.github.alighaziam.eom.inventory.infrastructure.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
interface SpringDataStockRepository extends JpaRepository<StockItemEntity,UUID>{
 @Modifying(clearAutomatically=true,flushAutomatically=true)
 @Query(value="""
 UPDATE inventory.stock_items
 SET reserved_quantity=reserved_quantity+:quantity, updated_at=CURRENT_TIMESTAMP
 WHERE product_id=:productId AND on_hand_quantity-reserved_quantity>=:quantity
 """,nativeQuery=true)
 int reserve(@Param("productId") UUID productId,@Param("quantity") int quantity);
}