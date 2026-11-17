CREATE TABLE payment.gateway_operations (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment.payment_intents(id),
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(120),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(36),
    locked_until TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_gateway_operation_type
        CHECK (operation_type IN ('AUTHORIZE', 'CAPTURE', 'VOID', 'REFUND')),
    CONSTRAINT ck_gateway_operation_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'UNKNOWN', 'RECONCILING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_gateway_operation_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_gateway_operation_lease
        CHECK ((locked_by IS NULL AND locked_until IS NULL)
            OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)),
    CONSTRAINT uq_gateway_operation_payment_type UNIQUE (payment_id, operation_type)
);

CREATE INDEX ix_gateway_operations_pending
    ON payment.gateway_operations(next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_gateway_operations_expired_lease
    ON payment.gateway_operations(locked_until)
    WHERE status IN ('PROCESSING', 'RECONCILING');