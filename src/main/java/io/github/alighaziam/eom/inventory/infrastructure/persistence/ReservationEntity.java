package io.github.alighaziam.eom.inventory.infrastructure.persistence;

import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations", schema = "inventory")
public class ReservationEntity {
    @Id private UUID id;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false) private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private ReservationStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ReservationEntity() {}

    ReservationEntity(Reservation reservation) {
        id = reservation.id();
        productId = reservation.productId();
        quantity = reservation.quantity();
        status = reservation.status();
        createdAt = reservation.createdAt();
        updatedAt = reservation.updatedAt();
    }

    Reservation toDomain() {
        return new Reservation(id, productId, quantity, status, createdAt, updatedAt);
    }
}
