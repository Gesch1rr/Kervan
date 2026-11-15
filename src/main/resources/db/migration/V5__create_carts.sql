-- INCREMENT BY must match @SequenceGenerator(allocationSize = 50) on Cart and CartItem.
CREATE SEQUENCE carts_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE cart_items_seq START WITH 1 INCREMENT BY 50;

-- One cart per user.
CREATE TABLE carts (
    id         BIGINT      PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id         BIGINT  PRIMARY KEY,
    cart_id    BIGINT  NOT NULL,
    product_id BIGINT  NOT NULL,
    quantity   INTEGER NOT NULL,
    -- the unique index also serves lookups by cart_id (its first column)
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
