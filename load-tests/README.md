# Load tests

k6 scripts that exercise the API through nginx in front of N app replicas (`compose.scale.yaml`), against a database seeded with 100k users. The tests are not part of CI; they are run manually on a laptop.

## Running

All commands are PowerShell, run from the repository root.

1. Stop the dev stack if it is running (it uses the same host ports).
2. Start the scaled stack:
   ```powershell
   docker compose -f compose.yaml -f compose.scale.yaml -p kervan-scale up -d --build --scale app=3
   ```
3. Seed the database once (~45 s; 100k users, 20k products, 300k orders, 600k order items; the password of every seeded user is `password123`):
   ```powershell
   Get-Content load-tests/seed/seed-100k.sql | docker compose -p kervan-scale exec -T postgres psql -v ON_ERROR_STOP=1 -U kervan -d kervan
   ```
4. Browse + checkout:
   ```powershell
   docker run --rm -i --network kervan-scale_default -v "${PWD}\load-tests:/scripts" -e BASE_URL=http://nginx -e PEAK_RPS=2000 -e LABEL=3-replicas grafana/k6:1.8.3 run /scripts/browse-checkout.js
   ```
5. Stock contention:
   ```powershell
   docker run --rm -i --network kervan-scale_default -v "${PWD}\load-tests:/scripts" -e BASE_URL=http://nginx grafana/k6:1.8.3 run /scripts/stock-contention.js
   ```

k6 runs inside the stack's Docker network and talks to nginx directly. Going through `host.docker.internal` also works, but then the Docker Desktop port forwarding is part of what gets measured.

Summaries are written to `load-tests/results/` (git-ignored). Environment variables: `PEAK_RPS` (default 200), `CHECKOUT_VUS` (default 20), `LABEL`.

## Scenarios

- **browse**: `ramping-arrival-rate` from 10 to `PEAK_RPS` req/s over 2 min, then 1 min at `PEAK_RPS`. Mix: 50% product list (page 0-9, every third request filtered by category), 40% product detail (Redis-cached), 10% categories. Threshold p95 < 300 ms, no dropped iterations.
- **checkout**: 20 VUs for 3 min, each with its own seeded user: add to cart -> place order -> list orders, 1 s think time. Threshold p95 < 800 ms.
- **stock-contention**: 100 buyers race for a product with stock 25. A buyer can be turned away at the cart (stock already 0) or at checkout; both count as a correct rejection.
- **Global**: failed requests < 1%.

## Results

Windows laptop, 16 cores, Docker Desktop, no other heavy programs running; every app replica limited to 1 CPU and 768 MB; nginx, PostgreSQL, Redis, RabbitMQ and k6 run on the same machine. k6 runs inside the stack's Docker network. Measured on 2026-11-15, each configuration after one warm-up run (JIT).

Browse + checkout (browse ramps to the peak rate, checkout runs 20 VUs throughout):

| | 1 replica, 1200 req/s | 3 replicas, 1200 req/s | 1 replica, 2000 req/s | 3 replicas, 2000 req/s |
|---|---|---|---|---|
| browse p95 / p99 | 16 / 75 ms | 5 / 8 ms | 357 / 647 ms (threshold failed) | 6 / 15 ms |
| checkout p95 / p99 | 79 / 128 ms | 34 / 83 ms | 378 / 832 ms | 26 / 74 ms |
| dropped iterations | 38 | 0 | 38,948 (about 16% of the requested load) | 0 |
| failed requests | 0% | 0% | 0% | 0% |
| requests served (3 min) | 154,678 | 155,100 | 209,937 | 251,098 |
| Redis cache hit ratio | 84% | 80% | 89% | 91% |

The hit ratio is the Redis `keyspace_hits / (hits + misses)` delta over each run; it covers the cached product detail and category list. The product list (half of the browse traffic) is not cached and costs two SQL statements per request (page + count).

Stock contention (3 replicas, two consecutive runs): logins and cart clearing happen in `setup()`, so the 100 buyers start at the same moment. Every run created exactly 25 orders, rejected the other 75 buyers with `409 INSUFFICIENT_STOCK` (at the cart or at checkout), ended with stock 0, and had 0 failed requests.

## What this shows

- At 1200 req/s one replica keeps up on average but has tail stalls (p99 75 ms, a few dropped iterations); three replicas cut p95 by 2-3x (browse 16 -> 5 ms, checkout 79 -> 34 ms) and drop nothing. At 2000 req/s one replica is saturated (p95 357 ms, 16% of the requested load not delivered) while three replicas serve all of it with p95 6 ms. Because the app is stateless (JWT, shared Redis cache, stock locking in PostgreSQL), adding replicas adds capacity, up to the point where the shared database or the single machine becomes the limit.
- Correctness holds across replicas: stock is decremented with a conditional `UPDATE` in PostgreSQL, so no instance can oversell, even with 100 buyers hitting three replicas at once.
- Capacity estimate for 100k users: assume 100k daily users and 10% of them (10k) active in the busiest hour. At one request per active user every 30 s that is about 330 req/s; at one every 5 s it is about 2,000 req/s. On this laptop one replica covers the lower estimate easily and three replicas cover the upper one. These are laptop numbers, not production numbers.

## Caveats and known limits

- Everything shares one machine, so scaling is sub-linear and the numbers are relative, not absolute.
- Not measured here: database CPU and connection usage, and the event consumer under load (that each order is confirmed once is covered by the integration tests, not by these runs).
- Each replica opens up to 20 database connections; replicas x 20 + headroom must stay below PostgreSQL `max_connections` (set to 200 in `compose.scale.yaml`). With many more replicas a connection pooler (PgBouncer) or read replicas would be needed.
- Product search (the `q` parameter) uses `LIKE` on `lower(name)` without a trigram index, so it scans the products table; a `pg_trgm` GIN index would be the fix. The load test does not exercise search for this reason.
- Seeded rows share 365 distinct `created_at` values, so "newest first" pages contain many ties.
- Order events are published after commit without an outbox; if RabbitMQ is down at that moment the event is lost (the order stays `PENDING`).
