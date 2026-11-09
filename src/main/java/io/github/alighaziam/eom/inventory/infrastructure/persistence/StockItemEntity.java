package io.github.alighaziam.eom.inventory.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="stock_items",schema="inventory")
public class StockItemEntity{
 @Id @Column(name="product_id") private UUID productId;
 @Column(name="on_hand_quantity",nullable=false) private int onHandQuantity;
 @Column(name="reserved_quantity",nullable=false) private int reservedQuantity;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected StockItemEntity(){}
 StockItemEntity(UUID id,int onHand,int reserved,Instant updated){productId=id;onHandQuantity=onHand;reservedQuantity=reserved;updatedAt=updated;}
 UUID getProductId(){return productId;} int getOnHandQuantity(){return onHandQuantity;} int getReservedQuantity(){return reservedQuantity;} Instant getUpdatedAt(){return updatedAt;}
}