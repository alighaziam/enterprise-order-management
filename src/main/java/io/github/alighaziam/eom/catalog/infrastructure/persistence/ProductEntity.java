package io.github.alighaziam.eom.catalog.infrastructure.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="products",schema="catalog")
public class ProductEntity {
 @Id private UUID id;
 @Column(nullable=false,length=64) private String sku;
 @Column(nullable=false,length=180) private String name;
 @Column(length=1000) private String description;
 @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal unitPrice;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false) private boolean active;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 protected ProductEntity(){}
 ProductEntity(UUID id,String sku,String name,String description,BigDecimal price,String currency,boolean active,Instant createdAt){
  this.id=id;this.sku=sku;this.name=name;this.description=description;this.unitPrice=price;this.currency=currency;this.active=active;this.createdAt=createdAt;
 }
 UUID getId(){return id;} String getSku(){return sku;} String getName(){return name;} String getDescription(){return description;}
 BigDecimal getUnitPrice(){return unitPrice;} String getCurrency(){return currency;} boolean isActive(){return active;} Instant getCreatedAt(){return createdAt;}
}