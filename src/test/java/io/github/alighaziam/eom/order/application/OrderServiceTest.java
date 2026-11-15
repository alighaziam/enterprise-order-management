package io.github.alighaziam.eom.order.application;

import io.github.alighaziam.eom.catalog.CatalogOperations;
import io.github.alighaziam.eom.catalog.CatalogProduct;
import io.github.alighaziam.eom.inventory.InventoryOperations;
import io.github.alighaziam.eom.order.domain.Order;
import io.github.alighaziam.eom.order.domain.OrderStatus;
import io.github.alighaziam.eom.order.OrderCancellationGuard;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    @Test
    void placesAndConfirmsOrderThroughPublishedModuleApis() {
        UUID customer = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        InMemoryStore store = new InMemoryStore();
        CatalogOperations catalog = id -> Optional.of(new CatalogProduct(
                product, "SKU-1", "Keyboard", new BigDecimal("10.00"), "EUR", true));
        InventoryOperations inventory = mock(InventoryOperations.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        when(inventory.reserve(product, 2)).thenReturn(reservation);

        OrderService service = new OrderService(store, id -> true, catalog, inventory, outbox, mock(OrderCancellationGuard.class));
        Order placed = service.place(customer, List.of(new OrderService.OrderRequestLine(product, 2)));

        assertEquals(OrderStatus.RESERVED, placed.status());
        assertEquals(new BigDecimal("20.00"), placed.totalAmount());
        assertEquals(reservation, placed.lines().getFirst().reservationId());
        assertEquals(OrderStatus.CONFIRMED, service.confirm(placed.id()).status());
        assertEquals(OrderStatus.CONFIRMED, service.confirm(placed.id()).status());

        verify(inventory, times(1)).confirm(reservation);
        verify(outbox).append(eq("OrderPlaced"), eq("Order"), eq(placed.id().toString()),
                eq(1L), eq(placed.id().toString()), isNull(), anyString());
        verify(outbox).append(eq("OrderConfirmed"), eq("Order"), eq(placed.id().toString()),
                eq(2L), eq(placed.id().toString()), isNull(), anyString());
    }

    @Test
    void cancellingOrderReleasesEachReservationOnceAndPublishesCancellation() {
        UUID product = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        InMemoryStore store = new InMemoryStore();
        InventoryOperations inventory = mock(InventoryOperations.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        when(inventory.reserve(product, 1)).thenReturn(reservation);
        CatalogOperations catalog = id -> Optional.of(new CatalogProduct(
                product, "SKU-2", "Mouse", new BigDecimal("5.00"), "EUR", true));

        OrderService service = new OrderService(store, id -> true, catalog, inventory, outbox, mock(OrderCancellationGuard.class));
        Order order = service.place(UUID.randomUUID(),
                List.of(new OrderService.OrderRequestLine(product, 1)));

        assertEquals(OrderStatus.CANCELLED, service.cancel(order.id()).status());
        assertEquals(OrderStatus.CANCELLED, service.cancel(order.id()).status());

        verify(inventory, times(1)).release(reservation);
        verify(outbox).append(eq("OrderCancelled"), eq("Order"), eq(order.id().toString()),
                eq(2L), eq(order.id().toString()), isNull(), anyString());
    }

    @Test
    void doesNotReleaseStockWhenAnActivePaymentBlocksCancellation() {
        UUID product = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        InMemoryStore store = new InMemoryStore();
        InventoryOperations inventory = mock(InventoryOperations.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        OrderCancellationGuard guard = mock(OrderCancellationGuard.class);
        when(inventory.reserve(product, 1)).thenReturn(reservation);
        when(guard.assertCanCancel(any())).thenThrow(
                new IllegalStateException("An active payment exists."));
        CatalogOperations catalog = id -> Optional.of(new CatalogProduct(
                product, "SKU-3", "Monitor", new BigDecimal("100.00"), "EUR", true));

        OrderService service = new OrderService(store, id -> true, catalog, inventory, outbox, guard);
        Order order = service.place(UUID.randomUUID(),
                List.of(new OrderService.OrderRequestLine(product, 1)));

        assertThrows(IllegalStateException.class, () -> service.cancel(order.id()));
        verify(inventory, never()).release(reservation);
        verify(guard).assertCanCancel(order.id());
        verify(outbox, never()).append(eq("OrderCancelled"), anyString(), anyString(),
                anyLong(), anyString(), any(), anyString());
    }

    @Test
    void rejectsMixedCurrencyAndDuplicateProductLinesBeforePersisting() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        InMemoryStore store = new InMemoryStore();
        InventoryOperations inventory = mock(InventoryOperations.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        when(inventory.reserve(first, 1)).thenReturn(reservation);
        CatalogOperations catalog = id -> Optional.of(new CatalogProduct(
                id, "SKU", "Product", BigDecimal.ONE, id.equals(first) ? "EUR" : "USD", true));

        OrderService service = new OrderService(store, id -> true, catalog, inventory, outbox, mock(OrderCancellationGuard.class));

        assertThrows(OrderConflictException.class, () -> service.place(UUID.randomUUID(), List.of(
                new OrderService.OrderRequestLine(first, 1),
                new OrderService.OrderRequestLine(second, 1))));
        assertThrows(OrderConflictException.class, () -> service.place(UUID.randomUUID(), List.of(
                new OrderService.OrderRequestLine(first, 1),
                new OrderService.OrderRequestLine(first, 2))));

        assertTrue(store.orders.isEmpty());
        verifyNoInteractions(outbox);
    }

    private static class InMemoryStore implements OrderStore {
        private final Map<UUID, Order> orders = new HashMap<>();

        public Order save(Order order) {
            orders.put(order.id(), order);
            return order;
        }

        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(orders.get(id));
        }

        public Optional<Order> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        public List<Order> findRecent() {
            return new ArrayList<>(orders.values());
        }

        public Page<Order> search(String query, io.github.alighaziam.eom.order.domain.OrderStatus status,
                                  Pageable pageable) {
            return Page.empty(pageable);
        }
    }
}
