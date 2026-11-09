package io.github.alighaziam.eom.inventory.domain;
import java.time.Instant;
import java.util.UUID;
public record StockItem(UUID productId,int onHandQuantity,int reservedQuantity,Instant updatedAt){
 public int availableQuantity(){return onHandQuantity-reservedQuantity;}
}