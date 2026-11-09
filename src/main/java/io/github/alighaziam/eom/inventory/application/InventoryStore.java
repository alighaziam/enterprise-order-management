package io.github.alighaziam.eom.inventory.application;

import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.StockItem;

import java.util.Optional;
import java.util.UUID;

public interface InventoryStore {
    StockItem save(StockItem item);
    Optional<StockItem> findByProductId(UUID productId);
    boolean reserve(UUID productId, int quantity);
    Reservation saveReservation(Reservation reservation);
    Optional<Reservation> findReservationForUpdate(UUID reservationId);
    boolean releaseReservedStock(UUID productId, int quantity);
    boolean confirmReservedStock(UUID productId, int quantity);
}
