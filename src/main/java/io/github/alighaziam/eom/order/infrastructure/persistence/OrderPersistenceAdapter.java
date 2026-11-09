package io.github.alighaziam.eom.order.infrastructure.persistence;
import io.github.alighaziam.eom.order.application.OrderStore;
import io.github.alighaziam.eom.order.domain.Order;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public class OrderPersistenceAdapter implements OrderStore{
 private final SpringDataOrderRepository repository;
 public OrderPersistenceAdapter(SpringDataOrderRepository repository){this.repository=repository;}
 public Order save(Order order){return repository.save(new OrderEntity(order)).toDomain();}
 public Optional<Order> findById(UUID id){return repository.findWithLinesById(id).map(OrderEntity::toDomain);}
 public Optional<Order> findByIdForUpdate(UUID id){return repository.findByIdForUpdate(id).map(OrderEntity::toDomain);}
}
