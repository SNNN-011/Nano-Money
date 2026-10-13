package com.example.ui.config

/**
 * Model AI untuk chat.
 *
 * Hanya satu model yang dipakai user, sisanya cadangan yang dicoba otomatis kalau
 * model utama tidak menjawab. Urutannya dikelola di sini, bukan lewat pilihan di
 * UI: tombol switch hanya menambah opsi yang bisa gagal, tanpa membuat alur lebih
 * mudah ditebak.
 *
 * Nama model di sini adalah format yang dibaca proxy Worker (lihat `modelMapping` di
 * `cloudflare-worker/src/worker.js`), yang juga sudah punya fallback sendiri. Jadi
 * aplikasi cukup mengirim apa adanya, tanpa memetakan ulang ke id model lain.
 */
object AiModelConfig {

    /** Model utama untuk chat. */
    const val PRIMARY_MODEL = "gemma-4-31b-it"

    /** Cadangan pertama, dipakai kalau model utama tidak menjawab. */
    const val FALLBACK_MODEL_1 = "gemini-3.1-flash-lite"

    /** Cadangan kedua. */
    const val FALLBACK_MODEL_2 = "gemini-3.5-flash-lite"

    /**
     * Batas waktu satu percobaan.
     *
     * Dulu 30 detik per model. Dengan beberapa model dalam ladder, satu kegagalan
     * bisa menahan layar hampir semenit sebelum errornya dilaporkan. Model yang hidup
     * biasanya menjawab dalam hitungan detik, jadi 10 detik sudah longgar.
     */
    const val ATTEMPT_TIMEOUT_MS = 10_000L

    /** Urutan percobaan: utama dulu, lalu cadangan. */
    val CHAT_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2)

    /** Model untuk pembaca struk. */
    val RECEIPT_MODELS: List<String> = listOf(FALLBACK_MODEL_2, PRIMARY_MODEL)

    fun modelsToTry(): List<String> = CHAT_MODELS.distinct()
}
