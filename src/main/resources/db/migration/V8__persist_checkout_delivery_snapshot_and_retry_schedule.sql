ALTER TABLE checkout.workflows
 ADD COLUMN recipient_name VARCHAR(180),
 ADD COLUMN address_line1 VARCHAR(240),
 ADD COLUMN address_line2 VARCHAR(240),
 ADD COLUMN city VARCHAR(120),
 ADD COLUMN postal_code VARCHAR(40),
 ADD COLUMN country_code VARCHAR(2),
 ADD COLUMN phone VARCHAR(40),
 ADD COLUMN fulfillment_attempts INTEGER NOT NULL DEFAULT 0,
 ADD COLUMN next_attempt_at TIMESTAMPTZ;
CREATE INDEX ix_checkout_fulfillment_retry
 ON checkout.workflows(status,next_attempt_at)
 WHERE status='FULFILLMENT_PENDING';