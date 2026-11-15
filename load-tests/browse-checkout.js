import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { API, SEED, randomInt, randomProductId, randomCategoryId } from './lib/config.js';
import { params, login } from './lib/http.js';

const PEAK_RPS = Number(__ENV.PEAK_RPS || 200);
const CHECKOUT_VUS = Number(__ENV.CHECKOUT_VUS || 20);
const LABEL = __ENV.LABEL || 'run';

const SHIPPING_ADDRESS = {
  line1: '1 Load Test Street',
  city: 'Istanbul',
  postalCode: '34000',
  country: 'TR',
};

// Module state is per VU in k6, so each checkout VU caches its own token.
let token;

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-arrival-rate',
      exec: 'browse',
      startRate: 10,
      timeUnit: '1s',
      // Enough VUs up front, so VU creation during the run does not distort latency.
      preAllocatedVUs: 200,
      maxVUs: 400,
      stages: [
        { target: PEAK_RPS, duration: '2m' },
        { target: PEAK_RPS, duration: '1m' },
      ],
    },
    checkout: {
      executor: 'constant-vus',
      exec: 'checkout',
      vus: CHECKOUT_VUS,
      duration: '3m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:browse}': ['p(95)<300'],
    'http_req_duration{scenario:checkout}': ['p(95)<800'],
    // Dropped iterations = the system could not keep up with the requested rate.
    dropped_iterations: ['count<1'],
  },
};

export function browse() {
  const r = Math.random();

  if (r < 0.5) {
    // 50%: product listing, every third call filtered by category.
    let url = `${API}/products?page=${randomInt(0, 9)}&size=20`;
    if (Math.random() < 1 / 3) {
      url += `&categoryId=${randomCategoryId()}`;
    }
    const res = http.get(url, params(null, 'GET /products'));
    check(res, { 'GET /products 200': (resp) => resp.status === 200 });
  } else if (r < 0.9) {
    // 40%: single product. Fixed name tag, otherwise every id becomes its own metric.
    const res = http.get(
      `${API}/products/${randomProductId()}`,
      params(null, 'GET /products/{id}'),
    );
    check(res, { 'GET /products/{id} 200': (resp) => resp.status === 200 });
  } else {
    // 10%: categories.
    const res = http.get(`${API}/categories`, params(null, 'GET /categories'));
    check(res, { 'GET /categories 200': (resp) => resp.status === 200 });
  }
}

export function checkout() {
  // __VU is unique across the test, so every checkout VU gets its own seeded user and its
  // own cart; logging in once per VU keeps BCrypt out of the measured loop (tokens live
  // 15 minutes, longer than a run).
  if (!token) {
    token = login(`loaduser${__VU}@load.kervan.local`, SEED.password);
  }

  const addRes = http.post(
    `${API}/cart/items`,
    JSON.stringify({ productId: randomProductId(), quantity: randomInt(1, 3) }),
    params(token, 'POST /cart/items'),
  );
  check(addRes, { 'POST /cart/items 200': (resp) => resp.status === 200 });

  const orderRes = http.post(
    `${API}/orders`,
    JSON.stringify({ shippingAddress: SHIPPING_ADDRESS }),
    params(token, 'POST /orders'),
  );
  check(orderRes, { 'POST /orders 201': (resp) => resp.status === 201 });

  const listRes = http.get(`${API}/orders?size=10`, params(token, 'GET /orders'));
  check(listRes, { 'GET /orders 200': (resp) => resp.status === 200 });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`/scripts/results/browse-checkout-${LABEL}.json`]: JSON.stringify(data, null, 2),
  };
}
