package io.github.alighaziam.eom.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID productId,
        int quantity,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public Reservation {
        if (id == null || productId == null || status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Reservation fields must not be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive.");
        }
    }

    public Reservation release(Instant now) {
        if (status == ReservationStatus.RELEASED) return this;
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservations can be released.");
        }
        return new Reservation(id, productId, quantity, ReservationStatus.RELEASED, createdAt, now);
    }

    public Reservation confirm(Instant now) {
        if (status == ReservationStatus.CONFIRMED) return this;
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only pending reservations can be confirmed.");
        }
        return new Reservation(id, productId, quantity, ReservationStatus.CONFIRMED, createdAt, now);
    }
}
