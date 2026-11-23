ALTER TABLE shipping.shipments
    ADD COLUMN label_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN label_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN label_locked_by VARCHAR(36),
    ADD COLUMN label_locked_until TIMESTAMPTZ,
    ADD COLUMN last_label_error VARCHAR(500);

ALTER TABLE shipping.shipments
    ADD CONSTRAINT ck_shipment_label_attempts CHECK (label_attempts >= 0),
    ADD CONSTRAINT ck_shipment_label_lease
        CHECK ((label_locked_by IS NULL AND label_locked_until IS NULL)
            OR (label_locked_by IS NOT NULL AND label_locked_until IS NOT NULL));

CREATE INDEX ix_shipments_pending_label_creation
    ON shipping.shipments(label_next_attempt_at, created_at)
    WHERE status = 'PENDING' AND carrier_reference IS NULL;
