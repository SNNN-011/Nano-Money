import test from 'node:test';
import assert from 'node:assert/strict';

import {
  checkAndIncrement,
  dayKey,
  isAllowedToken,
  rateLimitKey,
  validateRequest,
} from '../src/ratelimit.js';

/** KV tiruan minimal: Map + get/put, cukup untuk checkAndIncrement. */
function makeKV(store = new Map()) {
  const puts = [];
  return {
    store,
    puts,
    async get(key) {
      return store.has(key) ? store.get(key) : null;
    },
    async put(key, value, options) {
      puts.push({ key, value, options });
      store.set(key, value);
    },
  };
}

/** KV yang selalu error, untuk menguji jalur fail-open. */
const brokenKV = {
  async get() {
    throw new Error('KV down');
  },
  async put() {
    throw new Error('KV down');
  },
};

const DAY = Date.UTC(2026, 9, 15, 12, 0, 0); // 2026-10-15T12:00:00Z
const NEXT_DAY = Date.UTC(2026, 9, 16, 12, 0, 0);

test('di bawah limit: allowed dan count naik', async () => {
  const kv = makeKV();

  const first = await checkAndIncrement(kv, 'uid-1', 3, DAY);
  assert.deepEqual(first, { allowed: true, count: 1, limit: 3 });

  const second = await checkAndIncrement(kv, 'uid-1', 3, DAY);
  assert.equal(second.allowed, true);
  assert.equal(second.count, 2);

  assert.equal(kv.store.get(rateLimitKey('uid-1', DAY)), '2');
  assert.equal(kv.puts[0].options.expirationTtl, 86400);
});

test('tepat di limit: allowed false dan count tidak naik', async () => {
  const kv = makeKV(new Map([[rateLimitKey('uid-2', DAY), '3']]));

  const res = await checkAndIncrement(kv, 'uid-2', 3, DAY);

  assert.deepEqual(res, { allowed: false, count: 3, limit: 3 });
  assert.equal(kv.puts.length, 0, 'request yang ditolak tidak boleh menulis KV');
  assert.equal(kv.store.get(rateLimitKey('uid-2', DAY)), '3');
});

test('KV error: fail-open dengan tanda degraded', async () => {
  const res = await checkAndIncrement(brokenKV, 'uid-3', 3, DAY);

  assert.equal(res.allowed, true);
  assert.equal(res.degraded, true);
  assert.equal(res.limit, 3);
  assert.equal(res.count, null);
});

test('key berganti saat ganti hari (UTC)', async () => {
  assert.equal(dayKey(DAY), '2026-10-15');
  assert.equal(dayKey(NEXT_DAY), '2026-10-16');
  assert.notEqual(rateLimitKey('uid-4', DAY), rateLimitKey('uid-4', NEXT_DAY));

  // 23:59 vs 00:01 UTC harus jatuh ke bucket berbeda.
  const beforeMidnight = Date.UTC(2026, 9, 15, 23, 59, 59);
  const afterMidnight = Date.UTC(2026, 9, 16, 0, 0, 1);
  assert.notEqual(rateLimitKey('uid-4', beforeMidnight), rateLimitKey('uid-4', afterMidnight));

  // Fungsional: kuota habis hari ini, tersedia lagi besok.
  const kv = makeKV();
  assert.equal((await checkAndIncrement(kv, 'uid-4', 1, DAY)).allowed, true);
  assert.equal((await checkAndIncrement(kv, 'uid-4', 1, DAY)).allowed, false);
  assert.equal((await checkAndIncrement(kv, 'uid-4', 1, NEXT_DAY)).allowed, true);
});

test('validateRequest: GET ditolak 405', () => {
  const res = validateRequest('GET', '/v1beta/models/gemini-3.1-flash-lite:generateContent', null);
  assert.equal(res.ok, false);
  assert.equal(res.status, 405);
});

test('validateRequest: action selain generateContent ditolak 400', () => {
  const res = validateRequest('POST', '/v1beta/models/gemini-3.1-flash-lite:embedContent', null);
  assert.equal(res.ok, false);
  assert.equal(res.status, 400);
});

test('validateRequest: path dengan segmen ekstra ditolak 400', () => {
  const res = validateRequest('POST', '/v1beta/models/gemini-3.1-flash-lite:generateContent/extra', null);
  assert.equal(res.ok, false);
  assert.equal(res.status, 400);
});

test('validateRequest: body melebihi batas ditolak 413', () => {
  const res = validateRequest('POST', '/v1beta/models/gemini-3.1-flash-lite:generateContent', String(8_000_001));
  assert.equal(res.ok, false);
  assert.equal(res.status, 413);
});

test('validateRequest: POST + generateContent lolos', () => {
  const res = validateRequest('POST', '/v1beta/models/gemini-3.1-flash-lite:generateContent', '1024');
  assert.equal(res.ok, true);
  assert.equal(res.model, 'gemini-3.1-flash-lite');
  assert.equal(res.action, 'generateContent');
});

test('validateRequest: tanpa :action memakai default generateContent', () => {
  const res = validateRequest('POST', '/v1beta/models/gemini-3.1-flash-lite', null);
  assert.equal(res.ok, true);
  assert.equal(res.action, 'generateContent');
});

test('isAllowedToken: email belum terverifikasi ditolak', () => {
  assert.equal(
    isAllowedToken({ email_verified: false, firebase: { sign_in_provider: 'google.com' } }),
    false
  );
});

test('isAllowedToken: provider email/password ditolak', () => {
  assert.equal(
    isAllowedToken({ email_verified: true, firebase: { sign_in_provider: 'password' } }),
    false
  );
});

test('isAllowedToken: Google lolos', () => {
  assert.equal(
    isAllowedToken({ email_verified: true, firebase: { sign_in_provider: 'google.com' } }),
    true
  );
});

test('isAllowedToken: escape hatch allowAnyProvider meloloskan non-Google', () => {
  assert.equal(
    isAllowedToken({ email_verified: true, firebase: { sign_in_provider: 'password' } }, true),
    true
  );
});
