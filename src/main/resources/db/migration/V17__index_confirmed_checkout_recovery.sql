CREATE INDEX ix_checkout_confirmed_recovery
    ON checkout.workflows(updated_at)
    WHERE status = 'ORDER_CONFIRMED';
