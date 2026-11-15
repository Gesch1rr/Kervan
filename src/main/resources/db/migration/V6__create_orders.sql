-- INCREMENT BY must match @SequenceGenerator(allocationSize = 50) on Order and OrderItem.
CREATE SEQUENCE orders_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE order_items_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE orders (
    id               BIGINT         PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total_amount     NUMERIC(19, 2) NOT NULL,
    ship_line1       VARCHAR(200)   NOT NULL,
    ship_city        VARCHAR(100)   NOT NULL,
    ship_postal_code VARCHAR(20)    NOT NULL,
    ship_country     VARCHAR(100)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- "my orders, newest first"
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at DESC);
-- admin filter by status
CREATE INDEX idx_orders_status ON orders (status);

-- product_name and unit_price are copied at order time, so later product changes do not rewrite history.
CREATE TABLE order_items (
    id           BIGINT         PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(19, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
