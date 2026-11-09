package io.github.alighaziam.eom.inventory.application;

import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {
    @Test
    void reservesAndReleasesStockExactlyOnce() {
        InMemoryStore store = new InMemoryStore();
        InventoryService service = new InventoryService(store);
        UUID productId = UUID.randomUUID();
        service.initializeStock(productId, 10);

        Reservation pending = service.reserve(productId, 7);
        assertEquals(ReservationStatus.PENDING, pending.status());
        assertEquals(7, service.find(productId).reservedQuantity());
        assertEquals(3, service.find(productId).availableQuantity());

        Reservation released = service.release(pending.id());
        assertEquals(ReservationStatus.RELEASED, released.status());
        assertEquals(0, service.find(productId).reservedQuantity());

        assertEquals(ReservationStatus.RELEASED, service.release(pending.id()).status());
        assertEquals(0, service.find(productId).reservedQuantity());
    }

    @Test
    void confirmsReservationAndConsumesOnHandStock() {
        InMemoryStore store = new InMemoryStore();
        InventoryService service = new InventoryService(store);
        UUID productId = UUID.randomUUID();
        service.initializeStock(productId, 10);

        Reservation pending = service.reserve(productId, 4);
        Reservation confirmed = service.confirm(pending.id());

        assertEquals(ReservationStatus.CONFIRMED, confirmed.status());
        assertEquals(6, service.find(productId).onHandQuantity());
        assertEquals(0, service.find(productId).reservedQuantity());
        assertEquals(ReservationStatus.CONFIRMED, service.confirm(pending.id()).status());
    }

    @Test
    void preventsConfirmingReleasedReservation() {
        InventoryService service = new InventoryService(new InMemoryStore());
        UUID productId = UUID.randomUUID();
        service.initializeStock(productId, 5);
        Reservation reservation = service.reserve(productId, 2);
        service.release(reservation.id());

        assertThrows(IllegalStateException.class, () -> service.confirm(reservation.id()));
    }

    @Test
    void rejectsInsufficientAndNonPositiveReservations() {
        InventoryService service = new InventoryService(new InMemoryStore());
        UUID productId = UUID.randomUUID();
        service.initializeStock(productId, 2);

        assertThrows(IllegalArgumentException.class, () -> service.reserve(productId, 0));
        assertThrows(InsufficientStockException.class, () -> service.reserve(productId, 3));
    }

    private static class InMemoryStore implements InventoryStore {
        private final Map<UUID, StockItem> stocks = new HashMap<>();
        private final Map<UUID, Reservation> reservations = new HashMap<>();

        public StockItem save(StockItem item) {
            stocks.put(item.productId(), item);
            return item;
        }

        public Optional<StockItem> findByProductId(UUID id) {
            return Optional.ofNullable(stocks.get(id));
        }

        public boolean reserve(UUID id, int quantity) {
            StockItem current = stocks.get(id);
            if (current == null || current.availableQuantity() < quantity) return false;
            stocks.put(id, new StockItem(id, current.onHandQuantity(),
                    current.reservedQuantity() + quantity, Instant.now()));
            return true;
        }

        public Reservation saveReservation(Reservation reservation) {
            reservations.put(reservation.id(), reservation);
            return reservation;
        }

        public Optional<Reservation> findReservationForUpdate(UUID id) {
            return Optional.ofNullable(reservations.get(id));
        }

        public boolean releaseReservedStock(UUID id, int quantity) {
            StockItem current = stocks.get(id);
            if (current == null || current.reservedQuantity() < quantity) return false;
            stocks.put(id, new StockItem(id, current.onHandQuantity(),
                    current.reservedQuantity() - quantity, Instant.now()));
            return true;
        }

        public boolean confirmReservedStock(UUID id, int quantity) {
            StockItem current = stocks.get(id);
            if (current == null || current.reservedQuantity() < quantity
                    || current.onHandQuantity() < quantity) return false;
            stocks.put(id, new StockItem(id, current.onHandQuantity() - quantity,
                    current.reservedQuantity() - quantity, Instant.now()));
            return true;
        }
    }
}
