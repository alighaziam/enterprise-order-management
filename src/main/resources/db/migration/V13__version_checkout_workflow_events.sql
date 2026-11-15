ALTER TABLE checkout.workflows
    ADD COLUMN event_version BIGINT NOT NULL DEFAULT 1;

UPDATE checkout.workflows AS workflow
SET event_version = COALESCE((
    SELECT MAX(event.aggregate_version)
    FROM messaging.outbox_events AS event
    WHERE event.aggregate_type = 'CheckoutWorkflow'
      AND event.aggregate_id = workflow.id::text
), 1);

ALTER TABLE checkout.workflows
    ADD CONSTRAINT ck_checkout_event_version_positive
    CHECK (event_version > 0);
