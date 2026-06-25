import { createRemoteJWKSet, jwtVerify } from 'jose';

// Endpoint resmi JWKS dari Google untuk memverifikasi token Firebase Auth
const FIREBASE_JWKS_URI = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const JWKS = createRemoteJWKSet(new URL(FIREBASE_JWKS_URI));

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
    return payload;
  } catch (e) {
    console.error('Token verification failed:', e);
    return null;
  }
}

export default {
  async fetch(request, env, ctx) {
    // 1. Tangani CORS Preflight Request
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        }
      });
    }

    const projectId = env.FIREBASE_PROJECT_ID;
    if (!projectId) {
      return new Response(JSON.stringify({ error: "Server error: FIREBASE_PROJECT_ID belum diset di environment variables Worker" }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // 2. Ekstrak dan verifikasi token dari header Authorization
    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return new Response(JSON.stringify({ error: "Unauthorized: Header Authorization (Bearer token) tidak ditemukan" }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const token = authHeader.substring(7); // Hapus awalan "Bearer "
    const decodedToken = await verifyFirebaseToken(token, projectId);
    
    if (!decodedToken || !decodedToken.sub) {
      return new Response(JSON.stringify({ error: "Unauthorized: Token tidak valid atau sudah expired" }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const uid = decodedToken.sub; // Ini adalah UID user dari Firebase

    // 3. Rate Limiting dengan Cloudflare KV
    // env.RATE_LIMIT_KV adalah binding name yang Anda set di Cloudflare (bisa via wrangler.toml)
    if (env.RATE_LIMIT_KV) {
      // Buat key yang unik per user (UID) dan per hari
      const today = new Date().toISOString().split('T')[0];
      const kvKey = `ratelimit:${uid}:${today}`;
      
      // Ambil pemakaian saat ini (default 0)
      let currentUsage = parseInt(await env.RATE_LIMIT_KV.get(kvKey)) || 0;
      
      if (currentUsage >= 100) {
        return new Response(JSON.stringify({ error: "Rate limit exceeded: Batas maksimal 100 request per hari telah tercapai" }), {
          status: 429,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      
      // Update pemakaian dan set expiration (TTL 86400 detik = 24 jam agar KV tidak penuh)
      await env.RATE_LIMIT_KV.put(kvKey, (currentUsage + 1).toString(), { expirationTtl: 86400 });
    }

    // 4. Lanjutkan request ke Gemini API
    const url = new URL(request.url);
    
    // Ganti host dari request Worker ke host Gemini API
    const geminiUrl = new URL(`https://generativelanguage.googleapis.com${url.pathname}${url.search}`);
    // Sisipkan API Key Gemini
    geminiUrl.searchParams.set('key', env.GEMINI_API_KEY);

    // Buat headers baru untuk diteruskan, jangan teruskan header Authorization ke Gemini
    const headers = new Headers();
    const contentType = request.headers.get('Content-Type');
    if (contentType) {
        headers.set('Content-Type', contentType);
    }

    const init = {
      method: request.method,
      headers: headers,
    };
    
    // Copy body jika method selain GET/HEAD
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      init.body = await request.clone().arrayBuffer();
    }

    // Lakukan HTTP call ke Gemini
    const geminiResponse = await fetch(geminiUrl.toString(), init);

    // 5. Kembalikan response Gemini ke klien Android
    const responseHeaders = new Headers(geminiResponse.headers);
    responseHeaders.set('Access-Control-Allow-Origin', '*'); // Pastikan CORS tetap diizinkan
    
    // Karena Gemini kadang mengembalikan transfer-encoding: chunked yang bisa bermasalah jika proxy
    // menghapus content-encoding, lebih baik teruskan body as is.
    return new Response(geminiResponse.body, {
      status: geminiResponse.status,
      statusText: geminiResponse.statusText,
      headers: responseHeaders,
    });
  }
};
