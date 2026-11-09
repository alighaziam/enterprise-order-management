package io.github.alighaziam.eom.order.infrastructure.persistence;
import io.github.alighaziam.eom.order.domain.OrderLine;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Embeddable
public class OrderLineEmbeddable {
 @Column(name="line_id",nullable=false) private UUID id;
 @Column(name="product_id",nullable=false) private UUID productId;
 @Column(nullable=false,length=64) private String sku;
 @Column(name="product_name",nullable=false,length=180) private String productName;
 @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal unitPrice;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false) private int quantity;
 @Column(name="reservation_id",nullable=false) private UUID reservationId;
 protected OrderLineEmbeddable(){}
 OrderLineEmbeddable(OrderLine l){id=l.id();productId=l.productId();sku=l.sku();productName=l.productName();unitPrice=l.unitPrice();currency=l.currency();quantity=l.quantity();reservationId=l.reservationId();}
 OrderLine toDomain(){return new OrderLine(id,productId,sku,productName,unitPrice,currency,quantity,reservationId);}
}
