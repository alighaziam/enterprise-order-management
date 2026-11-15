ALTER TABLE shipping.shipments
    ADD COLUMN event_version BIGINT NOT NULL DEFAULT 1;

UPDATE shipping.shipments AS shipment
SET event_version = COALESCE((
    SELECT MAX(event.aggregate_version)
    FROM messaging.outbox_events AS event
    WHERE event.aggregate_type = 'Shipment'
      AND event.aggregate_id = shipment.id::text
), 1);

ALTER TABLE shipping.shipments
    ADD CONSTRAINT ck_shipment_event_version_positive
    CHECK (event_version > 0);
