package io.github.alighaziam.eom.order.application;
import io.github.alighaziam.eom.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface OrderStore {
 Order save(Order order);
 Optional<Order> findById(UUID id);
 Optional<Order> findByIdForUpdate(UUID id);
 List<Order> findRecent();
 Page<Order> search(String q, io.github.alighaziam.eom.order.domain.OrderStatus status, Pageable pageable);
}
