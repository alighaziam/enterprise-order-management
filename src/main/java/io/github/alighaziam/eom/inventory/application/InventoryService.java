package io.github.alighaziam.eom.inventory.application;

import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {
    private final InventoryStore store;

    public InventoryService(InventoryStore store) {
        this.store = store;
    }

    public StockItem initializeStock(UUID id, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Initial stock cannot be negative.");
        if (store.findByProductId(id).isPresent()) {
            throw new IllegalStateException("Stock has already been initialized for product: " + id);
        }
        return store.save(new StockItem(id, quantity, 0, Instant.now()));
    }

    public Reservation reserve(UUID productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive.");
        if (store.findByProductId(productId).isEmpty()) throw new StockItemNotFoundException(productId);
        if (!store.reserve(productId, quantity)) throw new InsufficientStockException(productId, quantity);

        Instant now = Instant.now();
        Reservation reservation = new Reservation(
                UUID.randomUUID(), productId, quantity, ReservationStatus.PENDING, now, now
        );
        return store.saveReservation(reservation);
    }

    public Reservation release(UUID reservationId) {
        Reservation reservation = findReservationForUpdate(reservationId);
        if (reservation.status() == ReservationStatus.RELEASED) return reservation;
        Reservation released = reservation.release(Instant.now());
        if (!store.releaseReservedStock(reservation.productId(), reservation.quantity())) {
            throw new IllegalStateException("Reserved stock invariant violated for reservation: " + reservationId);
        }
        return store.saveReservation(released);
    }

    public Reservation confirm(UUID reservationId) {
        Reservation reservation = findReservationForUpdate(reservationId);
        if (reservation.status() == ReservationStatus.CONFIRMED) return reservation;
        Reservation confirmed = reservation.confirm(Instant.now());
        if (!store.confirmReservedStock(reservation.productId(), reservation.quantity())) {
            throw new IllegalStateException("Reserved stock invariant violated for reservation: " + reservationId);
        }
        return store.saveReservation(confirmed);
    }

    @Transactional(readOnly = true)
    public StockItem find(UUID id) {
        return store.findByProductId(id).orElseThrow(() -> new StockItemNotFoundException(id));
    }

    private Reservation findReservationForUpdate(UUID id) {
        return store.findReservationForUpdate(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }
}
