package io.github.alighaziam.eom.order.infrastructure.persistence;
import io.github.alighaziam.eom.order.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="orders",schema="ordering")
public class OrderEntity {
 @Id private UUID id;
 @Column(name="customer_id",nullable=false) private UUID customerId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OrderStatus status;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal totalAmount;
 @ElementCollection @CollectionTable(name="order_lines",schema="ordering",joinColumns=@JoinColumn(name="order_id"))
 @OrderColumn(name="line_number") private List<OrderLineEmbeddable> lines=new ArrayList<>();
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected OrderEntity(){}
 OrderEntity(Order o){id=o.id();customerId=o.customerId();status=o.status();currency=o.currency();totalAmount=o.totalAmount();lines=new ArrayList<>(o.lines().stream().map(OrderLineEmbeddable::new).toList());createdAt=o.createdAt();updatedAt=o.updatedAt();}
 Order toDomain(){return new Order(id,customerId,status,currency,totalAmount,lines.stream().map(OrderLineEmbeddable::toDomain).toList(),createdAt,updatedAt);}
}
