ALTER TABLE checkout.workflows
    ADD COLUMN event_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE checkout.workflows
    ADD CONSTRAINT ck_checkout_event_version_positive
    CHECK (event_version > 0);
