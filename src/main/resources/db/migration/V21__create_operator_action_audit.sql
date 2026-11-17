CREATE SCHEMA IF NOT EXISTS operations;

CREATE TABLE operations.operator_actions (
    id UUID PRIMARY KEY,
    actor VARCHAR(200) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operator_action_name CHECK (action ~ '^[A-Z_]+$'),
    CONSTRAINT ck_operator_action_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_operator_actions_occurred_at
    ON operations.operator_actions(occurred_at DESC);

CREATE INDEX ix_operator_actions_target
    ON operations.operator_actions(target_type, target_id, occurred_at DESC);

CREATE INDEX ix_operator_actions_actor
    ON operations.operator_actions(actor, occurred_at DESC);
