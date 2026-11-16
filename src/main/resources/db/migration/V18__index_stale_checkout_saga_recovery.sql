CREATE INDEX ix_checkout_stale_saga_recovery
    ON checkout.workflows(updated_at, fulfillment_lease_until)
    WHERE status IN ('ORDER_RESERVED', 'PAYMENT_AUTHORIZED', 'PAYMENT_CAPTURED');
