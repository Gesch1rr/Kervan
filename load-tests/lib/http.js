import http from 'k6/http';
import { fail } from 'k6';
import { API } from './config.js';

// Shared request params: JSON content type, optional bearer token, optional metric name tag.
export function params(token, name) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const tags = name ? { name } : {};
  return { headers, tags };
}

export function login(email, password) {
  const res = http.post(
    `${API}/auth/login`,
    JSON.stringify({ email, password }),
    params(null, 'POST /auth/login'),
  );
  if (res.status !== 200) {
    fail(`login failed for ${email}: ${res.status}`);
  }
  return res.json('accessToken');
}
