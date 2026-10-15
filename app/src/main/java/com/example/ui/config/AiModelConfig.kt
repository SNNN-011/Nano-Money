package com.example.ui.config

/**
 * Model AI untuk chat.
 *
 * Aplikasi HANYA mengirim satu model ([PRIMARY_MODEL]); proxy Worker punya rantai
 * fallback sendiri (`MODEL_CHAINS` di `worker.js`). Sebelumnya kedua sisi
 * masing-masing me-loop semua model, sehingga satu tap user bisa memicu sampai
 * ~18 panggilan Gemini (3 model di app x 6 percobaan di Worker) yang hampir
 * semuanya hanya membakar kuota.
 *
 * Nama model di sini adalah format yang dibaca proxy Worker, jadi daftar ini
 * harus tetap sinkron dengan `MODEL_CHAINS` di `cloudflare-worker/src/worker.js`.
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
     * Worker membatasi total rantainya sendiri (~16 detik, lihat TOTAL_BUDGET_MS),
     * jadi batas ini hanya jaring pengaman agar layar tidak menggantung.
     */
    const val ATTEMPT_TIMEOUT_MS = 20_000L

    /** Daftar model yang dikenal Worker; urutannya urutan preferensi lama. */
    val CHAT_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2)

    /** Model untuk pembaca struk, diurut dari yang paling andal. */
    val RECEIPT_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL_2, FALLBACK_MODEL_1)

    /**
     * Model yang benar-benar dikirim aplikasi: satu saja.
     * Worker yang mencoba model lain bila model ini gagal.
     */
    fun modelsToTry(): List<String> = listOf(PRIMARY_MODEL)
}
