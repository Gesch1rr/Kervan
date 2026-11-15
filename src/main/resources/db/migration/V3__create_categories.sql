-- INCREMENT BY must match @SequenceGenerator(allocationSize = 50) on Category.
CREATE SEQUENCE categories_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE categories (
    id         BIGINT       PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);
