ALTER TABLE shipping.shipments
    ADD COLUMN event_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE shipping.shipments
    ADD CONSTRAINT ck_shipment_event_version_positive
    CHECK (event_version > 0);
