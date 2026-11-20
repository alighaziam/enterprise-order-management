CREATE INDEX ix_gateway_operations_status_updated_at
    ON payment.gateway_operations(status, updated_at);
