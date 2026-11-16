package io.github.alighaziam.eom.order.application;

import io.github.alighaziam.eom.catalog.CatalogOperations;
import io.github.alighaziam.eom.catalog.CatalogProduct;
import io.github.alighaziam.eom.customer.CustomerOperations;
import io.github.alighaziam.eom.inventory.InventoryOperations;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderCancellationGuard;
import io.github.alighaziam.eom.order.OrderConfirmationGuard;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.order.domain.Order;
import io.github.alighaziam.eom.order.domain.OrderLine;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class OrderService implements OrderOperations {

    private final OrderStore store;
    private final CustomerOperations customers;
    private final CatalogOperations catalog;
    private final InventoryOperations inventory;
    private final OutboxOperations outbox;
    private final OrderCancellationGuard cancellationGuard;
    private final OrderConfirmationGuard confirmationGuard;

    public OrderService(OrderStore store, CustomerOperations customers,
                        CatalogOperations catalog, InventoryOperations inventory,
                        OutboxOperations outbox, OrderCancellationGuard cancellationGuard,
                        OrderConfirmationGuard confirmationGuard) {
        this.store = store;
        this.customers = customers;
        this.catalog = catalog;
        this.inventory = inventory;
        this.outbox = outbox;
        this.cancellationGuard = cancellationGuard;
        this.confirmationGuard = confirmationGuard;
    }

    public Order place(UUID customerId, List<OrderRequestLine> requested) {
        return place(customerId, requested == null ? null : requested.stream()
                .map(line -> new OrderLineRequest(line.productId(), line.quantity())).toList());
    }

    public Order place(UUID customerId, Collection<OrderLineRequest> requested) {
        if (customerId == null) {
            throw new OrderConflictException("Customer ID is required.");
        }
        if (requested == null || requested.isEmpty()) {
            throw new OrderConflictException("An order must contain at least one line.");
        }
        if (!customers.customerExists(customerId)) {
            throw new OrderConflictException("Customer does not exist: " + customerId);
        }

        Set<UUID> seen = new HashSet<>();
        List<OrderLine> lines = new ArrayList<>();
        String currency = null;
        BigDecimal total = BigDecimal.ZERO;

        for (OrderLineRequest input : requested) {
            if (input == null || input.productId() == null || input.quantity() <= 0) {
                throw new OrderConflictException("Each order line requires a product and a positive quantity.");
            }
            if (!seen.add(input.productId())) {
                throw new OrderConflictException("Duplicate product lines are not allowed: " + input.productId());
            }

            CatalogProduct product = catalog.findActiveProduct(input.productId())
                    .orElseThrow(() -> new OrderConflictException(
                            "Product does not exist or is inactive: " + input.productId()));
            if (currency == null) {
                currency = product.currency();
            }
            if (!currency.equals(product.currency())) {
                throw new OrderConflictException("All order lines must use the same currency.");
            }

            UUID reservation;
            try {
                reservation = inventory.reserve(product.id(), input.quantity());
            } catch (RuntimeException exception) {
                throw new OrderConflictException("Unable to reserve stock for product "
                        + product.sku() + ".", exception);
            }

            OrderLine line = new OrderLine(UUID.randomUUID(), product.id(), product.sku(),
                    product.name(), product.unitPrice(), product.currency(), input.quantity(), reservation);
            lines.add(line);
            total = total.add(line.subtotal());
        }

        if (!fitsDatabaseAmount(total)) {
            throw new OrderConflictException(
                    "Order total exceeds NUMERIC(19,4): at most 15 integer and 4 fractional digits are supported.");
        }

        Instant now = Instant.now();
        Order saved = store.save(new Order(UUID.randomUUID(), customerId, OrderStatus.RESERVED,
                currency, total, lines, now, now));
        outbox.append("OrderPlaced", "Order", saved.id().toString(), 1,
                saved.id().toString(), null,
                "{\"customerId\":\"" + customerId + "\",\"totalAmount\":"
                        + saved.totalAmount().toPlainString() + ",\"currency\":\"" + saved.currency() + "\"}");
        return saved;
    }

    public Order confirm(UUID id) {
        Order order = findForUpdate(id);
        if (order.status() == OrderStatus.CONFIRMED) {
            return order;
        }
        if (order.status() != OrderStatus.RESERVED) {
            throw new OrderConflictException("Only reserved orders can be confirmed.");
        }
        confirmationGuard.assertCanConfirm(id);

        try {
            for (OrderLine line : order.lines()) {
                inventory.confirm(line.reservationId());
            }
        } catch (RuntimeException exception) {
            throw new OrderConflictException("Order confirmation failed; no partial confirmation was committed.",
                    exception);
        }

        Order confirmed = store.save(order.confirm(Instant.now()));
        outbox.append("OrderConfirmed", "Order", confirmed.id().toString(), 2,
                confirmed.id().toString(), null, "{\"status\":\"CONFIRMED\"}");
        return confirmed;
    }

    public Order cancel(UUID id) {
        Order order = findForUpdate(id);
        if (order.status() == OrderStatus.CANCELLED) {
            return order;
        }
        if (order.status() != OrderStatus.RESERVED) {
            throw new OrderConflictException("Only reserved orders can be cancelled.");
        }
        cancellationGuard.assertCanCancel(id);

        try {
            for (OrderLine line : order.lines()) {
                inventory.release(line.reservationId());
            }
        } catch (RuntimeException exception) {
            throw new OrderConflictException("Order cancellation failed; no partial release was committed.",
                    exception);
        }

        Order cancelled = store.save(order.cancel(Instant.now()));
        outbox.append("OrderCancelled", "Order", cancelled.id().toString(), 2,
                cancelled.id().toString(), null, "{\"status\":\"CANCELLED\"}");
        return cancelled;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return store.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> list() {
        return store.findRecent();
    }

    @Transactional(readOnly = true)
    public Page<Order> search(String query, String status, int page, int size) {
        OrderStatus parsed = status == null || status.isBlank() ? null : OrderStatus.valueOf(status);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return store.search(normalizedQuery, parsed,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public OrderSummary placeForCheckout(UUID customerId, Collection<OrderLineRequest> lines) {
        return summary(place(customerId, lines));
    }

    public OrderSummary confirmForCheckout(UUID id) {
        return summary(confirm(id));
    }

    public OrderSummary cancelForCheckout(UUID id) {
        return summary(cancel(id));
    }

    public OrderState state(UUID id) {
        return OrderState.valueOf(get(id).status().name());
    }

    @Override
    public java.util.Optional<OrderDetails> orderDetails(UUID id) {
        return store.findByIdForUpdate(id).map(order -> new OrderDetails(order.id(), order.totalAmount(),
                order.currency(), OrderState.valueOf(order.status().name())));
    }

    private static boolean fitsDatabaseAmount(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        int integerDigits = normalized.precision() - normalized.scale();
        return normalized.signum() > 0 && normalized.scale() <= 4 && integerDigits <= 15;
    }

    private OrderSummary summary(Order order) {
        return new OrderSummary(order.id(), order.totalAmount(), order.currency());
    }

    private Order findForUpdate(UUID id) {
        return store.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public record OrderRequestLine(UUID productId, int quantity) { }
}