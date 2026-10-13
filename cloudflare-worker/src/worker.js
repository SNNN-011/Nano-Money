import { createRemoteJWKSet, jwtVerify } from 'jose';

// Endpoint resmi JWKS dari Google untuk memverifikasi token Firebase Auth
const FIREBASE_JWKS_URI = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const JWKS = createRemoteJWKSet(new URL(FIREBASE_JWKS_URI));

const GEMINI_BASE = 'https://generativelanguage.googleapis.com';

// Liveness. 503 "high demand" bersifat sementara, jadi masih layak dicoba ulang.
const RETRYABLE_STATUS = new Set([429, 500, 502, 503, 504]);
const MAX_ATTEMPTS_PER_MODEL = 2;

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

    const url = new URL(request.url);
    const match = url.pathname.match(/^\/v1beta\/models\/([^:/?]+)(?::([^/?]+))?/);
    if (!match) {
      return jsonResponse({ error: 'Bad Request: Endpoint harus /v1beta/models/<model>:<action>' }, 400);
    }

    const requestedModel = match[1];
    const action = match[2] || 'generateContent';
    const chain = MODEL_CHAINS[requestedModel] || DEFAULT_CHAIN;

    const requestBlob = await request.blob();
    const contentType = request.headers.get('Content-Type');

    const callGemini = async (modelName) => {
      const targetUrl = new URL(`${GEMINI_BASE}/v1beta/models/${modelName}:${action}`);
      targetUrl.searchParams.set('key', env.GEMINI_API_KEY);

      const headers = new Headers();
      if (contentType) headers.set('Content-Type', contentType);

      return fetch(
        new Request(targetUrl.toString(), {
          method: request.method,
          headers,
          body: requestBlob,
        })
      );
    };

    // Rantai dicoba berurutan; satu model boleh dicoba ulang kalau statusnya
    // sementara. Kalau sebuah model gagal dan berikutnya juga gagal, status yang
    // dikembalikan adalah yang paling informatif, bukan sekadar respons terakhir.
    const attempts = [];
    let lastResponse = null;

    for (const modelName of chain) {
      for (let attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
        let response;
        try {
          response = await callGemini(modelName);
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

        if (attempt < MAX_ATTEMPTS_PER_MODEL) await sleep(400 * attempt);
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
