import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { API, SEED, randomInt } from './lib/config.js';
import { params, login } from './lib/http.js';

// Stock contention proof: 100 buyers race for one product with stock 25. Exactly 25 orders
// must be created, the other 75 must be rejected with 409 INSUFFICIENT_STOCK (at the cart or
// at checkout), and the stock must end at 0. Any other outcome (oversell or undersell) fails
// the thresholds/teardown.
//
// Run:
//   docker run --rm -i --network kervan-scale_default -v "${PWD}\load-tests:/scripts" \
//     -e BASE_URL=http://nginx \
//     grafana/k6:1.8.3 run /scripts/stock-contention.js

const BUYERS = 100;
const STOCK = 25;
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@kervan.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin12345'; // dev-only default; the scaled stack reads it from .env

const SHIPPING_ADDRESS = {
  line1: '1 Load Test Street',
  city: 'Istanbul',
  postalCode: '34000',
  country: 'TR',
};

const orders_created = new Counter('orders_created');
const stock_conflicts = new Counter('stock_conflicts');

export const options = {
  scenarios: {
    race: {
      executor: 'per-vu-iterations',
      vus: BUYERS,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    orders_created: [`count==${STOCK}`],
    stock_conflicts: [`count==${BUYERS - STOCK}`],
    http_req_failed: ['rate<0.01'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  const runId = Date.now();

  const catRes = http.post(
    `${API}/categories`,
    JSON.stringify({ name: `Contention ${runId}` }),
    params(token, 'POST /categories'),
  );
  if (catRes.status !== 201) {
    fail(`create category failed: ${catRes.status} ${catRes.body}`);
  }

  const prodRes = http.post(
    `${API}/products`,
    JSON.stringify({
      name: `Contention Product ${runId}`,
      price: 10.0,
      stock: STOCK,
      categoryId: catRes.json('id'),
    }),
    params(token, 'POST /products'),
  );
  if (prodRes.status !== 201) {
    fail(`create product failed: ${prodRes.status} ${prodRes.body}`);
  }

  // Different users on every run, so carts and orders from earlier runs never interfere.
  const offset = randomInt(20000, 99000 - BUYERS);

  // Logins (BCrypt) and cart clearing happen before the race, so the 100 VUs start buying
  // at the same moment instead of being spread over seconds.
  const tokens = [];
  for (let i = 1; i <= BUYERS; i++) {
    const buyerToken = login(`loaduser${offset + i}@load.kervan.local`, SEED.password);
    const clearRes = http.del(`${API}/cart`, null, params(buyerToken, 'DELETE /cart'));
    if (clearRes.status !== 204) {
      fail(`DELETE /cart failed for user ${offset + i}: ${clearRes.status} ${clearRes.body}`);
    }
    tokens.push(buyerToken);
  }

  return { productId: prodRes.json('id'), tokens };
}

export default function (data) {
  const token = data.tokens[__VU - 1];

  // A buyer can be turned away at either step, depending on timing: at the cart (stock
  // already 0 when adding) or at checkout (stock ran out between adding and ordering).
  const addRes = http.post(
    `${API}/cart/items`,
    JSON.stringify({ productId: data.productId, quantity: 1 }),
    { ...params(token, 'POST /cart/items'), responseCallback: http.expectedStatuses(200, 409) },
  );

  if (addRes.status === 409 && addRes.json('code') === 'INSUFFICIENT_STOCK') {
    stock_conflicts.add(1);
    check(addRes, { 'added to cart or INSUFFICIENT_STOCK': () => true });
    return;
  }

  check(addRes, { 'added to cart or INSUFFICIENT_STOCK': (r) => r.status === 200 });

  // A 409 here is the correct answer when stock is gone, so it must not count as a failed
  // request.
  const orderRes = http.post(
    `${API}/orders`,
    JSON.stringify({ shippingAddress: SHIPPING_ADDRESS }),
    { ...params(token, 'POST /orders'), responseCallback: http.expectedStatuses(201, 409) },
  );

  if (orderRes.status === 201) {
    orders_created.add(1);
  } else if (orderRes.status === 409 && orderRes.json('code') === 'INSUFFICIENT_STOCK') {
    stock_conflicts.add(1);
  }

  check(orderRes, {
    'order created or INSUFFICIENT_STOCK': (r) =>
      r.status === 201 || (r.status === 409 && r.json('code') === 'INSUFFICIENT_STOCK'),
  });
}

export function teardown(data) {
  // Fresh read: during the race nothing calls the cached GET /products/{id} (cart and
  // checkout read stock directly from the database), and every stock change evicts this
  // product from the cache.
  const res = http.get(
    `${API}/products/${data.productId}`,
    params(null, 'GET /products/{id}'),
  );
  const stock = res.json('stock');
  console.log(`final stock for product ${data.productId}: ${stock}`);
  if (stock !== 0) {
    fail(`oversold or undersold: stock is ${stock}, expected 0`);
  }
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`/scripts/results/stock-contention-${__ENV.LABEL || 'run'}.json`]: JSON.stringify(data, null, 2),
  };
}
