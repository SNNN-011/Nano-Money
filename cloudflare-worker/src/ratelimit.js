/**
 * Utilitas rate limit harian dan validasi request untuk Worker.
 *
 * Modul ini sengaja bebas dari runtime Cloudflare (tidak mengimpor API wrangler)
 * supaya bisa diuji langsung dengan `node --test`. Binding KV hanya dipakai lewat
 * parameter `kv` yang butuh `get`/`put` — cukup diganti Map di test.
 */

/**
 * TTL KV untuk counter harian. Kunci counter sudah mengandung tanggal, jadi TTL
 * 24 jam hanya berfungsi membereskan kunci lama, bukan penentu periode kuota.
 */
const COUNTER_TTL_SECONDS = 86_400;

/** Batas ukuran body default (8 MB), dipakai Worker kalau tidak di-override. */
export const DEFAULT_MAX_BYTES = 8_000_000;

/** Satu-satunya action Gemini yang diizinkan lewat proxy ini. */
export const DEFAULT_ACTION = 'generateContent';

/**
 * Bucket harian berbasis UTC.
 * @param {number} nowMs Epoch milidetik
 * @returns {string} Tanggal UTC, mis. "2026-10-15"
 */
export function dayKey(nowMs) {
  return new Date(nowMs).toISOString().slice(0, 10);
}

/**
 * Kunci KV per uid per hari.
 * @param {string} uid Firebase UID (payload.sub)
 * @param {number} nowMs Epoch milidetik
 * @returns {string} mis. "rl:abc123:2026-10-15"
 */
export function rateLimitKey(uid, nowMs) {
  return `rl:${uid}:${dayKey(nowMs)}`;
}

/**
 * Membaca counter harian dan menaikkannya dalam satu langkah.
 *
 * Perilaku:
 * - Kunci belum ada -> dianggap count 0.
 * - count >= limit -> `allowed:false` TANPA menulis ulang KV (hemat write).
 * - Masih di bawah limit -> tulis count+1 dengan TTL 24 jam.
 * - KV error -> fail-open: `{ allowed:true, degraded:true, count:null }`, supaya
 *   gangguan KV tidak mematikan fitur yang sedang dilindungi.
 *
 * Catatan: kenaikan ini bukan operasi atomik sejati — dua request paralel pada
 * saat yang sama bisa membaca count N lalu menulis N+1 yang sama (satu hitungan
 * hangus). Untuk kuota 100/hari dampaknya kecil; kalau nanti butuh presisi tinggi
 * (billing), pindahkan counter ke Durable Object.
 *
 * @param {{ get: (key: string) => Promise<string|null>, put: (key: string, value: string, opts: { expirationTtl: number }) => Promise<void> }} kv Binding KV
 * @param {string} uid Firebase UID
 * @param {number} limitPerDay Batas request per hari
 * @param {number} nowMs Epoch milidetik
 * @returns {Promise<{ allowed: boolean, count: number|null, limit: number, degraded?: boolean }>}
 */
export async function checkAndIncrement(kv, uid, limitPerDay, nowMs) {
  const key = rateLimitKey(uid, nowMs);
  const limit = Number.isFinite(limitPerDay) && limitPerDay > 0
    ? Math.floor(limitPerDay)
    : 100;

  try {
    const raw = await kv.get(key);
    const parsed = raw === null || raw === undefined ? 0 : Number.parseInt(raw, 10);
    const count = Number.isFinite(parsed) && parsed > 0 ? parsed : 0;

    if (count >= limit) {
      return { allowed: false, count, limit };
    }

    await kv.put(key, String(count + 1), { expirationTtl: COUNTER_TTL_SECONDS });
    return { allowed: true, count: count + 1, limit };
  } catch (e) {
    return { allowed: true, count: null, limit, degraded: true };
  }
}

/**
 * Regex path endpoint Gemini. Di-anchor `$` supaya segmen ekstra seperti
 * `/v1beta/models/x:generateContent/extra` tidak ikut lolos.
 */
const MODEL_PATH_RE = /^\/v1beta\/models\/([^:\/?]+)(?::([^\/?]+))?$/;

/**
 * Validasi method, path, dan ukuran body sebelum request diteruskan ke Gemini.
 *
 * @param {string} method HTTP method dari klien
 * @param {string} pathname Path tanpa query string
 * @param {string|null|undefined} contentLength Nilai header Content-Length; null/undefined = lewatkan
 * @param {number} [maxBytes] Batas body (default 8 MB)
 * @returns {{ ok: true, model: string, action: string } | { ok: false, status: number, error: string }}
 */
export function validateRequest(method, pathname, contentLength, maxBytes = DEFAULT_MAX_BYTES) {
  if (method !== 'POST') {
    return { ok: false, status: 405, error: `Method ${method} tidak diizinkan; gunakan POST` };
  }

  const match = pathname.match(MODEL_PATH_RE);
  if (!match) {
    return { ok: false, status: 400, error: 'Bad Request: Endpoint harus /v1beta/models/<model>:<action>' };
  }

  const model = match[1];
  const action = match[2] || DEFAULT_ACTION;
  if (action !== DEFAULT_ACTION) {
    return { ok: false, status: 400, error: `Bad Request: action "${action}" tidak didukung (hanya ${DEFAULT_ACTION})` };
  }

  // Header tidak ada (chunked) atau tidak valid -> tidak bisa diperiksa di sini,
  // jadi dilewatkan; pembatasan sesungguhnya tetap di sisi Gemini/Cloudflare.
  if (contentLength !== null && contentLength !== undefined && contentLength !== '') {
    const len = Number(contentLength);
    if (Number.isFinite(len) && len > maxBytes) {
      return { ok: false, status: 413, error: `Payload terlalu besar: ${len} byte (maks ${maxBytes})` };
    }
  }

  return { ok: true, model, action };
}

/**
 * Memeriksa apakah payload token Firebase boleh memakai proxy ini.
 *
 * Syarat: email sudah diverifikasi DAN provider login adalah Google, kecuali
 * escape hatch `allowAnyProvider` (env ALLOW_ANY_PROVIDER=true) dipakai — mis.
 * untuk testing akun non-Google.
 *
 * @param {object|null|undefined} payload Payload JWT hasil verifikasi Firebase
 * @param {boolean} [allowAnyProvider] Lewati syarat provider (escape hatch)
 * @returns {boolean}
 */
export function isAllowedToken(payload, allowAnyProvider = false) {
  if (!payload) return false;
  if (payload.email_verified !== true) return false;
  if (allowAnyProvider === true) return true;
  return payload.firebase?.sign_in_provider === 'google.com';
}
