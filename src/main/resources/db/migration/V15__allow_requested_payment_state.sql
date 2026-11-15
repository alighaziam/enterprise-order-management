ALTER TABLE payment.payment_intents
    DROP CONSTRAINT IF EXISTS payment_intents_status_check;

ALTER TABLE payment.payment_intents
    ADD CONSTRAINT ck_payment_intents_status
    CHECK (status IN ('REQUESTED','AUTHORIZED','CAPTURED','FAILED','VOIDED','REFUND_PENDING','REFUNDED'));
