package io.github.alighaziam.eom.inventory.application;

import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.StockItem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryStore {
    StockItem save(StockItem item);
    Optional<StockItem> findByProductId(UUID productId);
    List<StockItem> findAllStock();
    Page<StockItem> search(String availability, Pageable pageable);
    boolean reserve(UUID productId, int quantity);
    Reservation saveReservation(Reservation reservation);
    Optional<Reservation> findReservationForUpdate(UUID reservationId);
    boolean releaseReservedStock(UUID productId, int quantity);
    boolean confirmReservedStock(UUID productId, int quantity);
}
