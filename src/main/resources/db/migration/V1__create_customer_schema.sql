CREATE SCHEMA IF NOT EXISTS customer;

CREATE TABLE customer.customers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email)
);
