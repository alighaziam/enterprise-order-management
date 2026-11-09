package io.github.alighaziam.eom.order.application;
import io.github.alighaziam.eom.order.domain.Order;
import java.util.Optional;
import java.util.UUID;
public interface OrderStore { Order save(Order order); Optional<Order> findById(UUID id); Optional<Order> findByIdForUpdate(UUID id); }
