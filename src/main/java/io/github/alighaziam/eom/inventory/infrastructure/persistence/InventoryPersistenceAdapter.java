package io.github.alighaziam.eom.inventory.infrastructure.persistence;

import io.github.alighaziam.eom.inventory.application.InventoryStore;
import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryPersistenceAdapter implements InventoryStore {
    private final SpringDataStockRepository stockRepository;
    private final SpringDataReservationRepository reservationRepository;

    public InventoryPersistenceAdapter(
            SpringDataStockRepository stockRepository,
            SpringDataReservationRepository reservationRepository
    ) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public StockItem save(StockItem item) {
        return toDomain(stockRepository.save(new StockItemEntity(
                item.productId(), item.onHandQuantity(), item.reservedQuantity(), item.updatedAt()
        )));
    }

    @Override
    public Optional<StockItem> findByProductId(UUID id) {
        return stockRepository.findById(id).map(InventoryPersistenceAdapter::toDomain);
    }

    @Override
    public List<StockItem> findAllStock() {
        return stockRepository.findAll().stream().map(InventoryPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Page<StockItem> search(String availability, Pageable pageable) {
        return stockRepository.search(availability, pageable).map(InventoryPersistenceAdapter::toDomain);
    }

    @Override
    public boolean reserve(UUID id, int quantity) {
        return stockRepository.reserve(id, quantity) == 1;
    }

    @Override
    public Reservation saveReservation(Reservation reservation) {
        return reservationRepository.save(new ReservationEntity(reservation)).toDomain();
    }

    @Override
    public Optional<Reservation> findReservationForUpdate(UUID id) {
        return reservationRepository.findByIdForUpdate(id).map(ReservationEntity::toDomain);
    }

    @Override
    public boolean releaseReservedStock(UUID id, int quantity) {
        return stockRepository.releaseReserved(id, quantity) == 1;
    }

    @Override
    public boolean confirmReservedStock(UUID id, int quantity) {
        return stockRepository.confirmReserved(id, quantity) == 1;
    }

    private static StockItem toDomain(StockItemEntity e) {
        return new StockItem(e.getProductId(), e.getOnHandQuantity(), e.getReservedQuantity(), e.getUpdatedAt());
    }
}
