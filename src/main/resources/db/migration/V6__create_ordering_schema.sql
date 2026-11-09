CREATE SCHEMA IF NOT EXISTS ordering;
CREATE TABLE ordering.orders (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL, status VARCHAR(20) NOT NULL,
 currency VARCHAR(3) NOT NULL, total_amount NUMERIC(19,4) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_orders_status CHECK(status IN ('RESERVED','CONFIRMED','CANCELLED')),
 CONSTRAINT ck_orders_total_positive CHECK(total_amount>0)
);
CREATE INDEX ix_orders_customer_created ON ordering.orders(customer_id,created_at DESC);
CREATE TABLE ordering.order_lines (
 order_id UUID NOT NULL, line_number INTEGER NOT NULL, line_id UUID NOT NULL,
 product_id UUID NOT NULL, sku VARCHAR(64) NOT NULL, product_name VARCHAR(180) NOT NULL,
 unit_price NUMERIC(19,4) NOT NULL, currency VARCHAR(3) NOT NULL, quantity INTEGER NOT NULL,
 reservation_id UUID NOT NULL,
 CONSTRAINT pk_order_lines PRIMARY KEY(order_id,line_number),
 CONSTRAINT uk_order_lines_line_id UNIQUE(line_id),
 CONSTRAINT uk_order_lines_reservation UNIQUE(reservation_id),
 CONSTRAINT fk_order_lines_order FOREIGN KEY(order_id) REFERENCES ordering.orders(id) ON DELETE CASCADE,
 CONSTRAINT ck_order_lines_quantity_positive CHECK(quantity>0),
 CONSTRAINT ck_order_lines_price_positive CHECK(unit_price>0)
);
CREATE INDEX ix_order_lines_product ON ordering.order_lines(product_id);
