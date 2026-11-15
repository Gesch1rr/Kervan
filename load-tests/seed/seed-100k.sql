-- =============================================================================
-- Kervan load-test seed data
-- =============================================================================
-- Purpose:
--   Populate a scaled Docker stack with realistic data volume for load tests:
--   100,000 users, 50 categories, 20,000 products, 300,000 orders and roughly
--   600,000 order items.
--
--   This file is NOT a Flyway migration. It is run manually against the
--   load-test stack only and must never be applied to production.
--
-- Run from the repository root in PowerShell:
--   Get-Content load-tests/seed/seed-100k.sql | docker compose -p kervan-scale exec -T postgres psql -v ON_ERROR_STOP=1 -U kervan -d kervan
--
-- Every seeded user has the password: password123
--
-- Id ranges:
--   users:       1_000_001 .. 1_100_000
--   categories:  1_000_001 .. 1_000_050
--   products:    1_000_001 .. 1_020_000
--   orders:      1_000_001 .. 1_300_000
--   order_items: 1_000_001 upward
--
--   k6 relies on these fixed ranges:
--     loaduser{n}@load.kervan.local where n = id - 1_000_000
--     product ids 1_000_001 .. 1_020_000
-- =============================================================================

BEGIN;

-- Fail fast if this seed has already been applied.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE email = 'loaduser1@load.kervan.local'
    ) THEN
        RAISE EXCEPTION 'seed already applied';
    END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Hash the shared password once and reuse it for all users.
-- Hashing 100,000 times would take minutes; a single bcrypt hash is enough
-- because every load-test user uses the same password.
-- pgcrypto's bf hashes use the $2a$ prefix, which Spring Security's
-- BCryptPasswordEncoder accepts.
CREATE TEMP TABLE seed_hash AS
SELECT crypt('password123', gen_salt('bf', 10)) AS h;

-- -----------------------------------------------------------------------------
-- Users: 100,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO users (
    id,
    email,
    password_hash,
    full_name,
    role,
    enabled,
    created_at,
    updated_at
)
SELECT
    1_000_000 + g.n,
    'loaduser' || g.n::text || '@load.kervan.local',
    sh.h,
    'Load User ' || g.n::text,
    'USER',
    TRUE,
    now() - (g.n % 365) * interval '1 day',
    now() - (g.n % 365) * interval '1 day'
FROM generate_series(1, 100000) AS g(n)
CROSS JOIN seed_hash AS sh;

-- -----------------------------------------------------------------------------
-- Categories: 50 rows
-- -----------------------------------------------------------------------------
INSERT INTO categories (
    id,
    name,
    slug,
    created_at,
    updated_at
)
SELECT
    1_000_000 + g.n,
    'Load Category ' || g.n::text,
    'load-category-' || g.n::text,
    now() - (g.n % 365) * interval '1 day',
    now() - (g.n % 365) * interval '1 day'
FROM generate_series(1, 50) AS g(n);

-- -----------------------------------------------------------------------------
-- Products: 20,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO products (
    id,
    name,
    slug,
    description,
    price,
    stock,
    category_id,
    active,
    version,
    created_at,
    updated_at
)
SELECT
    1_000_000 + g.n,
    'Load Product ' || g.n::text,
    'load-product-' || g.n::text,
    'Seeded product ' || g.n::text,
    round((5 + (g.n % 500) * 1.37)::numeric, 2),
    1000000,
    1_000_000 + 1 + (g.n % 50),
    TRUE,
    0,
    now() - (g.n % 365) * interval '1 day',
    now() - (g.n % 365) * interval '1 day'
FROM generate_series(1, 20000) AS g(n);

-- -----------------------------------------------------------------------------
-- Orders: 300,000 rows, 3 per user on average
-- -----------------------------------------------------------------------------
INSERT INTO orders (
    id,
    user_id,
    status,
    total_amount,
    ship_line1,
    ship_city,
    ship_postal_code,
    ship_country,
    created_at,
    updated_at
)
SELECT
    1_000_000 + g.n,
    1_000_000 + 1 + (g.n % 100000),
    CASE
        WHEN g.n % 10 BETWEEN 0 AND 5 THEN 'DELIVERED'
        WHEN g.n % 10 IN (6, 7) THEN 'CONFIRMED'
        WHEN g.n % 10 = 8 THEN 'PENDING'
        ELSE 'CANCELLED'
    END,
    0::numeric,
    'Seed Street ' || g.n::text,
    'Istanbul',
    '34000',
    'TR',
    now() - (g.n % 365) * interval '1 day' - (g.n % 1440) * interval '1 minute',
    now() - (g.n % 365) * interval '1 day' - (g.n % 1440) * interval '1 minute'
FROM generate_series(1, 300000) AS g(n);

-- -----------------------------------------------------------------------------
-- Order items: 1 to 3 items per order, roughly 600,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO order_items (
    id,
    order_id,
    product_id,
    product_name,
    unit_price,
    quantity
)
SELECT
    row_number() OVER () + 1_000_000,
    o.id,
    p.id,
    p.name,
    p.price,
    1 + ((o.n + i.item_no) % 3)
FROM (
    SELECT
        id,
        (id - 1_000_000)::integer AS n
    FROM orders
    WHERE id BETWEEN 1_000_001 AND 1_300_000
) AS o
CROSS JOIN LATERAL generate_series(1, 1 + (o.n % 3)) AS i(item_no)
JOIN products AS p
    ON p.id = 1_000_000 + 1 + ((o.n * 7 + i.item_no * 13) % 20000);

-- -----------------------------------------------------------------------------
-- Fill order totals from their items
-- -----------------------------------------------------------------------------
UPDATE orders AS o
SET total_amount = t.total_amount
FROM (
    SELECT
        order_id,
        sum(unit_price * quantity) AS total_amount
    FROM order_items
    WHERE order_id > 1_000_000
    GROUP BY order_id
) AS t
WHERE o.id = t.order_id
  AND o.id > 1_000_000;

-- -----------------------------------------------------------------------------
-- Move sequences past the seeded ids so the application never generates a
-- duplicate. With INCREMENT BY 50 the next allocated block starts above max(id).
-- -----------------------------------------------------------------------------
SELECT setval('users_seq', (SELECT max(id) FROM users));
SELECT setval('categories_seq', (SELECT max(id) FROM categories));
SELECT setval('products_seq', (SELECT max(id) FROM products));
SELECT setval('orders_seq', (SELECT max(id) FROM orders));
SELECT setval('order_items_seq', (SELECT max(id) FROM order_items));

COMMIT;

-- Fresh statistics so the planner uses the indexes. Runs after COMMIT.
ANALYZE users, categories, products, orders, order_items;

-- Final row counts.
SELECT 'users' AS table_name, count(*) AS row_count FROM users
UNION ALL
SELECT 'categories', count(*) FROM categories
UNION ALL
SELECT 'products', count(*) FROM products
UNION ALL
SELECT 'orders', count(*) FROM orders
UNION ALL
SELECT 'order_items', count(*) FROM order_items;
