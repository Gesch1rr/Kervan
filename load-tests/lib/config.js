export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
export const API = `${BASE_URL}/api/v1`;

// Must match load-tests/seed/seed-100k.sql
export const SEED = {
  users: 100000,
  productIdMin: 1000001,
  productIdMax: 1020000,
  categoryIdMin: 1000001,
  categoryIdMax: 1000050,
  password: 'password123',
};

// Inclusive on both ends.
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function randomProductId() {
  return randomInt(SEED.productIdMin, SEED.productIdMax);
}

export function randomCategoryId() {
  return randomInt(SEED.categoryIdMin, SEED.categoryIdMax);
}
