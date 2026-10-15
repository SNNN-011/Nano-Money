import { createRemoteJWKSet, jwtVerify } from 'jose';
import { checkAndIncrement, isAllowedToken, validateRequest } from './ratelimit.js';

// Endpoint resmi JWKS dari Google untuk memverifikasi token Firebase Auth
const FIREBASE_JWKS_URI = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const JWKS = createRemoteJWKSet(new URL(FIREBASE_JWKS_URI));

const GEMINI_BASE = 'https://generativelanguage.googleapis.com';

// Liveness. 503 "high demand" bersifat sementara, jadi masih layak dicoba ulang.
// 429 juga. 500 tidak: dari pengukuran, 500 pada model gemma muncul konsisten dan
// hanya menambah satu putaran penuh sebelum pindah ke model lain.
const RETRYABLE_STATUS = new Set([429, 503]);
const MAX_ATTEMPTS_PER_MODEL = 2;

// Batas waktu per panggilan ke Gemini. Tanpa ini satu request bisa menggantung
// 40 detik (terukur), dan seluruh rantai ikut tertahan.
const ATTEMPT_TIMEOUT_MS = 8000;

// Batas total waktu seluruh rantai fallback. App Android memutus request di
// ~20 detik, jadi seluruh percobaan + jeda retry harus selesai sebelum itu.
const TOTAL_BUDGET_MS = 16_000;

// Batas maksimum tidur saat menghormati header Retry-After dari Gemini;
// Retry-After besar tidak boleh menahan request sampai app timeout.
const MAX_RETRY_AFTER_MS = 2000;

// Batas ukuran body yang diterima Worker. Dicek dari header Content-Length
// SEBELUM body ditampung di memori.
const MAX_BODY_BYTES = 8_000_000;

// Kuota default request per uid per hari (UTC) kalau RATE_LIMIT_PER_DAY tidak diset.
const DEFAULT_RATE_LIMIT_PER_DAY = 100;

// Supaya peringatan "KV belum di-bind" hanya tercetak sekali per isolate,
// bukan di setiap request.
let kvMissingWarned = false;

/**
 * Model yang dipakai aplikasi, dipetakan ke urutan percobaan.
 *
 * Nama di kiri harus persis sama dengan yang dikirim `AiModelConfig` di sisi Android.
 * Nama di kanan adalah id yang benar-benar ada di Gemini API; diverifikasi dengan
 * memanggil daftar model, bukan ditebak dari nama.
 *
 * Catatan penting: `gemma-4-26b-it` TIDAK ada di Gemini API (selalu 404). Yang ada
 * `gemma-4-26b-a4b-it`. Salah tulis di sini membuat seluruh request gagal, karena
 * fallback Worker memakai nama itu.
 */
const MODEL_CHAINS = {
  // Chat: model utama, lalu dua cadangan.
  'gemma-4-31b-it': ['gemma-4-31b-it', 'gemini-3.1-flash-lite', 'gemini-3.5-flash-lite'],
  'gemini-3.1-flash-lite': ['gemini-3.1-flash-lite', 'gemma-4-31b-it', 'gemini-3.5-flash-lite'],
  'gemini-3.5-flash-lite': ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemma-4-31b-it'],
};

/** Rantai default untuk nama yang tidak dikenal: pakai dua model paling andal. */
const DEFAULT_CHAIN = ['gemini-3.1-flash-lite', 'gemma-4-31b-it'];

/**
 * Memverifikasi token Firebase Auth.
 * @param {string} idToken Token dari header Authorization
 * @param {string} projectId ID Project Firebase Anda
 * @returns {object|null} Payload JWT jika valid, null jika invalid
 */
async function verifyFirebaseToken(idToken, projectId) {
  try {
    const { payload } = await jwtVerify(idToken, JWKS, {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
    });
    if (!payload.sub || !payload.auth_time) return null;
    return payload;
  } catch (e) {
    console.error('Token verification failed:', e?.code || e?.message || 'invalid token');
    return null;
  }
}

function jsonResponse(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Sanggup menunggu sedikit sebelum mencoba ulang model yang sedang sibuk. */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Mengubah header Retry-After menjadi milidetik. Gemini mengirim jumlah detik
 * ("12"), tapi spesifikasi HTTP juga mengizinkan tanggal; kalau tidak bisa
 * diParse, kembalikan null supaya pemanggil memakai backoff default.
 * @param {string|null|undefined} value
 * @returns {number|null}
 */
export function parseRetryAfterMs(value) {
  if (!value) return null;
  const seconds = Number(value);
  // Angka negatif ditolak di sini; kalau jatuh ke Date.parse, string seperti
  // "-5" malah dianggap tahun dan menghasilkan nilai besar yang salah.
  if (Number.isFinite(seconds)) return seconds >= 0 ? Math.round(seconds * 1000) : null;
  const dateMs = Date.parse(value);
  if (Number.isNaN(dateMs)) return null;
  return Math.max(0, dateMs - Date.now());
}

export default {
  async fetch(request, env) {
    const projectId = env.FIREBASE_PROJECT_ID;
    if (!projectId) {
      return jsonResponse({ error: 'Server error: FIREBASE_PROJECT_ID belum diset di Worker' }, 500);
    }

    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return jsonResponse({ error: 'Unauthorized: Header Authorization (Bearer token) tidak ditemukan' }, 401);
    }

    const decodedToken = await verifyFirebaseToken(authHeader.substring(7), projectId);
    if (!decodedToken || !decodedToken.sub) {
      return jsonResponse({ error: 'Unauthorized: Token tidak valid atau sudah expired' }, 401);
    }
    const uid = decodedToken.sub;

    // Hanya token dari login Google dengan email terverifikasi yang boleh memakai
    // proxy ini. ALLOW_ANY_PROVIDER=true adalah escape hatch (mis. akun testing
    // non-Google); default tetap Google-only.
    if (!isAllowedToken(decodedToken, env.ALLOW_ANY_PROVIDER === 'true')) {
      return jsonResponse({ error: 'Forbidden: Token harus dari login Google dengan email terverifikasi' }, 403);
    }

    const url = new URL(request.url);
    const validation = validateRequest(
      request.method,
      url.pathname,
      request.headers.get('Content-Length'),
      MAX_BODY_BYTES
    );
    if (!validation.ok) {
      return jsonResponse({ error: validation.error }, validation.status);
    }

    const requestedModel = validation.model;
    const action = validation.action;

    // Rate limit harian per uid, diperiksa SEBELUM Gemini dipanggil supaya kuota
    // API tidak ikut terbakar oleh request yang sudah pasti ditolak.
    //
    // Fail-open kalau binding KV belum ada: deployment lama (sebelum namespace
    // KV dibuat di wrangler.toml) tetap melayani request tanpa limit, hanya
    // dengan satu console.warn — lebih baik begitu daripada seluruh fitur mati.
    if (env.RATE_LIMIT) {
      const parsedLimit = Number.parseInt(env.RATE_LIMIT_PER_DAY, 10);
      const limitPerDay = Number.isInteger(parsedLimit) && parsedLimit > 0
        ? parsedLimit
        : DEFAULT_RATE_LIMIT_PER_DAY;

      const rl = await checkAndIncrement(env.RATE_LIMIT, uid, limitPerDay, Date.now());
      if (rl.degraded) {
        console.warn(`[uid:${uid}] Rate limit degraded (KV error); request diloloskan`);
      } else if (!rl.allowed) {
        return jsonResponse(
          {
            error: 'Kuota harian habis. Coba lagi besok (kuota dihitung per hari UTC).',
            limit: rl.limit,
            count: rl.count,
          },
          429
        );
      }
    } else if (!kvMissingWarned) {
      kvMissingWarned = true;
      console.warn('RATE_LIMIT (KV) belum di-bind; rate limit dilewati. Lihat instruksi di wrangler.toml.');
    }

    const chain = MODEL_CHAINS[requestedModel] || DEFAULT_CHAIN;

    const requestBlob = await request.blob();
    const contentType = request.headers.get('Content-Type');

    const callGemini = async (modelName, timeoutMs) => {
      const targetUrl = new URL(`${GEMINI_BASE}/v1beta/models/${modelName}:${action}`);
      targetUrl.searchParams.set('key', env.GEMINI_API_KEY);

      const headers = new Headers();
      if (contentType) headers.set('Content-Type', contentType);

      return fetch(
        new Request(targetUrl.toString(), {
          method: request.method,
          headers,
          body: requestBlob,
        }),
        { signal: AbortSignal.timeout(timeoutMs) }
      );
    };

    // Rantai dicoba berurutan; satu model boleh dicoba ulang kalau statusnya
    // sementara. Kalau sebuah model gagal dan berikutnya juga gagal, status yang
    // dikembalikan adalah yang paling informatif, bukan sekadar respons terakhir.
    const attempts = [];
    let lastResponse = null;
    const startMs = Date.now();

    modelLoop:
    for (const modelName of chain) {
      for (let attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
        // Sisa anggaran waktu; kalau habis, berhenti daripada menahan request
        // sampai app menyerah duluan.
        const remaining = TOTAL_BUDGET_MS - (Date.now() - startMs);
        if (remaining <= 0) break modelLoop;

        let response;
        try {
          response = await callGemini(modelName, Math.max(1, Math.min(ATTEMPT_TIMEOUT_MS, remaining)));
        } catch (err) {
          console.error(`[uid:${uid}] ${modelName} gagal terhubung: ${err.message}`);
          attempts.push(`${modelName}: network error`);
          lastResponse = null;
          break;
        }

        if (response.ok) return response;

        const status = response.status;
        attempts.push(`${modelName}: HTTP ${status}`);
        lastResponse = response;

        if (!RETRYABLE_STATUS.has(status)) break;

        if (attempt < MAX_ATTEMPTS_PER_MODEL) {
          // Hormati Retry-After kalau ada, tapi jangan lebih lama dari sisa
          // anggaran waktu dan batas maksimum.
          const retryAfterMs = parseRetryAfterMs(response.headers?.get?.('Retry-After'));
          const sleepMs = Math.min(
            retryAfterMs ?? 300 * attempt,
            MAX_RETRY_AFTER_MS,
            Math.max(0, TOTAL_BUDGET_MS - (Date.now() - startMs))
          );
          if (sleepMs > 0) await sleep(sleepMs);
        }
      }
    }

    console.warn(`[uid:${uid}] Semua model gagal untuk "${requestedModel}" -> ${attempts.join(' | ')}`);

    const status = lastResponse?.status && !RETRYABLE_STATUS.has(lastResponse.status)
      ? lastResponse.status
      : 502;

    return jsonResponse(
      {
        error: 'Semua model gagal merespons.',
        details: attempts.join(' | '),
        requestedModel,
      },
      status
    );
  },
};
