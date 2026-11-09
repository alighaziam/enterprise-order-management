ALTER TABLE messaging.outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(80),
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

UPDATE messaging.outbox_events
SET next_attempt_at = created_at
WHERE next_attempt_at IS NULL;

CREATE INDEX ix_outbox_claimable
    ON messaging.outbox_events(next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX ix_outbox_dead_letters
    ON messaging.outbox_events(dead_lettered_at, created_at)
    WHERE dead_lettered_at IS NOT NULL;
