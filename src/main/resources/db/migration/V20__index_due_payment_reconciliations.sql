CREATE INDEX ix_gateway_operations_unknown_due
    ON payment.gateway_operations(next_attempt_at, updated_at, created_at)
    WHERE status = 'UNKNOWN';
