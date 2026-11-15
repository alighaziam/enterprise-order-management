package io.github.alighaziam.eom.shipping.application;

import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.Shipment;
import io.github.alighaziam.eom.shipping.ShipmentStatus;
import io.github.alighaziam.eom.shipping.ShippingOperations;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentEntity;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShippingService implements ShippingOperations {

    private final ShipmentRepository repository;
    private final OutboxOperations outbox;
    private final OrderOperations orders;

    public ShippingService(ShipmentRepository repository, OutboxOperations outbox, OrderOperations orders) {
        this.repository = repository;
        this.outbox = outbox;
        this.orders = orders;
    }

    @Override
    @Transactional
    public Shipment create(UUID orderId, DeliveryAddress address) {
        validate(orderId, address);

        OrderOperations.OrderDetails order = orders.orderDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist: " + orderId));
        if (order.state() != OrderState.CONFIRMED) {
            throw new IllegalStateException("A shipment can only be created for a confirmed order.");
        }

        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Shipment shipment = existing.get().toDomain();
            if (!shipment.destination().equals(address)) {
                throw new IllegalStateException(
                        "A shipment already exists for this order with a different destination.");
            }
            return shipment;
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        ShipmentEntity entity = repository.save(new ShipmentEntity(id, orderId, address, now));
        outbox.append("ShipmentCreated", "Shipment", id.toString(), 1,
                orderId.toString(), null, "{\"shipmentId\":\"" + id + "\"}");
        return entity.toDomain();
    }

    @Override
    @Transactional
    public Shipment updateStatus(UUID id, ShipmentStatus next) {
        ShipmentEntity entity = repository.findByIdForUpdate(id).orElseThrow(() -> new ShipmentNotFoundException(id));
        ShipmentStatus current = entity.toDomain().status();
        if (current == next) {
            return entity.toDomain();
        }

        boolean allowed = switch (current) {
            case PENDING -> next == ShipmentStatus.LABEL_CREATED || next == ShipmentStatus.CANCELLED;
            case LABEL_CREATED -> next == ShipmentStatus.READY_FOR_DISPATCH || next == ShipmentStatus.CANCELLED;
            case READY_FOR_DISPATCH -> next == ShipmentStatus.DISPATCHED || next == ShipmentStatus.CANCELLED;
            case DISPATCHED -> next == ShipmentStatus.IN_TRANSIT || next == ShipmentStatus.DELIVERY_FAILED;
            case IN_TRANSIT -> next == ShipmentStatus.DELIVERED || next == ShipmentStatus.DELIVERY_FAILED;
            case DELIVERY_FAILED -> next == ShipmentStatus.RETURNED;
            case DELIVERED, RETURNED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Invalid shipment transition: " + current + " -> " + next);
        }

        entity.status(next, Instant.now());
        outbox.append("Shipment" + next.name(), "Shipment", id.toString(), entity.getEventVersion(),
                entity.toDomain().orderId().toString(), null,
                "{\"shipmentId\":\"" + id + "\",\"status\":\"" + next + "\"}");
        return entity.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Shipment get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ShipmentNotFoundException(id)).toDomain();
    }

    @Transactional(readOnly = true)
    public List<Shipment> list() {
        return repository.findTop40ByOrderByCreatedAtDesc().stream().map(ShipmentEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public Page<Shipment> search(String query, String status, int page, int size) {
        ShipmentStatus parsed = status == null || status.isBlank() ? null : ShipmentStatus.valueOf(status);
        return repository.search(query == null || query.isBlank() ? null : query.trim(), parsed,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(ShipmentEntity::toDomain);
    }

    private static void validate(UUID orderId, DeliveryAddress address) {
        if (orderId == null || address == null || blank(address.recipientName())
                || blank(address.addressLine1()) || blank(address.city())
                || address.countryCode() == null || !address.countryCode().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "Order and complete delivery address with ISO country code are required.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
