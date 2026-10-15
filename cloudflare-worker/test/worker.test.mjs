import test from 'node:test';
import assert from 'node:assert/strict';

import { parseRetryAfterMs } from '../src/worker.js';

test('Retry-After detik diubah ke milidetik', () => {
  assert.equal(parseRetryAfterMs('12'), 12_000);
  assert.equal(parseRetryAfterMs('0'), 0);
  assert.equal(parseRetryAfterMs('0.5'), 500);
});

test('Retry-After yang tidak ada / tidak valid diabaikan', () => {
  assert.equal(parseRetryAfterMs(null), null);
  assert.equal(parseRetryAfterMs(undefined), null);
  assert.equal(parseRetryAfterMs(''), null);
  assert.equal(parseRetryAfterMs('abc'), null);
  // Angka negatif bukan format yang sah; jangan sampai jadi tidur negatif.
  assert.equal(parseRetryAfterMs('-5'), null);
});

test('Retry-After berupa tanggal HTTP dihitung dari waktu sekarang', () => {
  const target = Date.now() + 3_000;
  const parsed = parseRetryAfterMs(new Date(target).toUTCString());
  assert.ok(parsed !== null, 'tanggal HTTP harus bisa diParse');
  assert.ok(parsed > 0 && parsed <= 4_000, `nilai di luar dugaan: ${parsed}`);
});

test('tanggal HTTP yang sudah lewat menjadi 0, bukan negatif', () => {
  assert.equal(parseRetryAfterMs(new Date(Date.now() - 60_000).toUTCString()), 0);
});
