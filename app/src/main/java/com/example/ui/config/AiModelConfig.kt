package com.example.ui.config

/**
 * Konfigurasi model AI.
 *
 * [GEMINI_MODEL] dan [GEMMA_MODEL] hanya LABEL yang ditampilkan dan dipilih user.
 * Keduanya bukan id model yang benar-benar ada di Google, jadi tidak boleh dikirim
 * apa adanya: Gemini akan membalas 404 "model not found", dan `gemma-*` justru
 * ditolak 403 oleh whitelist path di proxy Worker.
 *
 * Yang dikirim ke API adalah pasangannya di [WIRE_MODEL_BY_LABEL].
 */
object AiModelConfig {

    /** Label di UI untuk pilihan cepat. */
    const val GEMINI_MODEL = "gemini-3.5-flash-lite"
    const val GEMMA_MODEL = "gemma-4-31b-it"

    /** Id model asli yang dipakai di request. */
    const val GEMINI_WIRE_MODEL = "gemini-2.0-flash"
    const val GEMMA_WIRE_MODEL = "gemini-1.5-flash"

    /**
     * Batas waktu satu percobaan.
     *
     * Dulu 30 detik per model. Dengan beberapa model dalam ladder, satu kegagalan
     * bisa menahan layar hampir semenit sebelum errornya dilaporkan, padahal model
     * berikutnya juga akan gagal. Model yang hidup biasanya menjawab dalam hitungan
     * detik, jadi 10 detik sudah longgar.
     */
    const val ATTEMPT_TIMEOUT_MS = 10_000L

    private val WIRE_MODEL_BY_LABEL = mapOf(
        GEMINI_MODEL to GEMINI_WIRE_MODEL,
        GEMMA_MODEL to GEMMA_WIRE_MODEL
    )

    /** Label UI -> id model yang dikirim. Label tak dikenal memakai default yang aman. */
    fun wireModel(label: String): String = WIRE_MODEL_BY_LABEL[label] ?: GEMMA_WIRE_MODEL

    /**
     * Urutan percobaan: model terpilih dulu, lalu model lain sebagai cadangan.
     * Semua id di sini harus benar-benar ada, dan hanya berawalan `gemini-` supaya
     * lolos whitelist path di Worker.
     */
    fun modelsToTry(label: String): List<String> =
        listOf(wireModel(label), GEMMA_WIRE_MODEL, GEMINI_WIRE_MODEL).distinct()
}
