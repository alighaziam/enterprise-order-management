CREATE SCHEMA IF NOT EXISTS inventory;
CREATE TABLE inventory.stock_items (
 product_id UUID PRIMARY KEY, on_hand_quantity INTEGER NOT NULL, reserved_quantity INTEGER NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL, CONSTRAINT ck_stock_on_hand_non_negative CHECK (on_hand_quantity >= 0),
 CONSTRAINT ck_stock_reserved_non_negative CHECK (reserved_quantity >= 0),
 CONSTRAINT ck_stock_reserved_not_over_on_hand CHECK (reserved_quantity <= on_hand_quantity)
);