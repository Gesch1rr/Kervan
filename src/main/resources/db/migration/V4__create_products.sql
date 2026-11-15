-- INCREMENT BY must match @SequenceGenerator(allocationSize = 50) on Product.
CREATE SEQUENCE products_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE products (
    id          BIGINT         PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL,
    slug        VARCHAR(220)   NOT NULL,
    description TEXT,
    price       NUMERIC(19, 2) NOT NULL,
    stock       INTEGER        NOT NULL,
    category_id BIGINT         NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    -- last line of defence: stock can never go negative, whatever the application does
    CONSTRAINT ck_products_stock CHECK (stock >= 0),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX idx_products_category_id ON products (category_id);
-- serves the default public listing: active products, newest first
CREATE INDEX idx_products_active_created_at ON products (active, created_at DESC);
