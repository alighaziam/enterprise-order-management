ALTER TABLE checkout.workflows
    ADD COLUMN fulfillment_locked_by VARCHAR(36),
    ADD COLUMN fulfillment_lease_until TIMESTAMPTZ;

CREATE INDEX ix_checkout_fulfillment_claim
    ON checkout.workflows(next_attempt_at, fulfillment_lease_until, updated_at)
    WHERE status = 'FULFILLMENT_PENDING';
