ALTER TABLE checkout.workflows
    ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL DEFAULT 'legacy';
