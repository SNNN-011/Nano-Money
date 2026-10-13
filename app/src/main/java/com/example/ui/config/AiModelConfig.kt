package com.example.ui.config

/**
 * Model AI untuk chat.
 *
 * Fallback TIDAK diatur berlapis di sini. Proxy Worker sudah menangani rantai model
 * beserta retry-nya, jadi satu permintaan dari aplikasi bisa menyentuh seluruh
 * rantai. Mencoba beberapa model dari aplikasi hanya memperpanjang waktu tunggu
 * tanpa menambah peluang berhasil.
 *
 * Urutan di bawah hanya urutan preferensi. Angkanya berasal dari pengukuran latency
 * nyata ke /v1beta/models dengan kunci API, bukan tebakan nama model.
 *
 * Nama model di sini adalah format yang dibaca proxy Worker (lihat `MODEL_CHAINS` di
 * `cloudflare-worker/src/worker.js`), yang juga sudah punya fallback sendiri.
 */
object AiModelConfig {

    /**
     * Model utama untuk chat.
     *
     * `gemma-4-31b-it` sempat menjadi pilihan awal, tapi hasil pengukuran: 1 dari 3
     * request hanging sampai 40 detik, sisanya membalas 500. `gemini-3.1-flash-lite`
     * satu-satunya yang selalu membalas 200, biasanya sekitar 3 detik.
     */
    const val PRIMARY_MODEL = "gemini-3.1-flash-lite"

    /** Cadangan pertama. */
    const val FALLBACK_MODEL_1 = "gemma-4-31b-it"

    /** Cadangan kedua; sering membalas 503 "high demand". */
    const val FALLBACK_MODEL_2 = "gemini-3.5-flash-lite"

    /**
     * Batas waktu satu percobaan di sisi aplikasi.
     *
     * Dulu 30 detik per model, jadi dengan beberapa model dalam ladder layar bisa
     * menggantung hampir semenit. Sekarang cukup sedikit di atas batas per-panggilan
     * Worker (8 detik x 2 percobaan + waktu perpindahan model).
     */
    const val ATTEMPT_TIMEOUT_MS = 20_000L

    /** Urutan percobaan: utama dulu, lalu cadangan. */
    val CHAT_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2)

    /** Model untuk pembaca struk, diurut dari yang paling andal. */
    val RECEIPT_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL_2, FALLBACK_MODEL_1)

    fun modelsToTry(): List<String> = CHAT_MODELS.distinct()
}
