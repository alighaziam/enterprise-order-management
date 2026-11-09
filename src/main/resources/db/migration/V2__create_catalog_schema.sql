CREATE SCHEMA IF NOT EXISTS catalog;
CREATE TABLE catalog.products (
 id UUID PRIMARY KEY, sku VARCHAR(64) NOT NULL, name VARCHAR(180) NOT NULL,
 description VARCHAR(1000), unit_price NUMERIC(19,4) NOT NULL, currency CHAR(3) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_products_sku UNIQUE (sku), CONSTRAINT ck_products_unit_price_positive CHECK (unit_price > 0)
);