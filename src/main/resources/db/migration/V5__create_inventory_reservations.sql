CREATE TABLE inventory.reservations (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'RELEASED', 'CONFIRMED'))
);

CREATE INDEX ix_reservations_product_status
    ON inventory.reservations (product_id, status);

CREATE INDEX ix_reservations_created_at
    ON inventory.reservations (created_at);
