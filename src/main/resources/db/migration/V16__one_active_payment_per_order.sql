CREATE UNIQUE INDEX uq_payment_one_active_per_order
    ON payment.payment_intents(order_id)
    WHERE status IN ('REQUESTED','AUTHORIZED','CAPTURED','REFUND_PENDING');
