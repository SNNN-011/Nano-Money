package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Otak teks lokal: satu permintaan masuk, satu balasan JSON keluar.
 *
 * Dipisah dari pembungkus native-nya supaya penerjemah di lapisan domain
 * ([com.example.domain.LocalTransactionParser]) bisa diuji di JVM tanpa mesin
 * Android.
 */
interface LocalEngine {
    suspend fun complete(text: String, maxNewTokens: Int = 128): String
}

/**
 * Mesin lokal sungguhan: membaca model dari aset APK sekali, menyiapkan prefix
 * sekali per hari, lalu menjawab satu kalimat per permintaan.
 *
 * Prompt prefix-nya SENGAJA cuma baris tanggal ("date: 2026-10-19 Mon 05:30"),
 * persis bentuk yang dipakai saat melatih model (lihat data_app/train.jsonl).
 * Prompt besar ala Gemini tidak dipakai di sini: modelnya tidak dilatih dengan
 * bentuk itu, jadi menambahkannya justru menurunkan akurasi.
 */
class NeedleLocalEngine(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : LocalEngine {

    private val mutex = Mutex()
    private var modelLoaded = false
    private var preparedPrefix: String? = null

    override suspend fun complete(text: String, maxNewTokens: Int): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureReady()
                // Setiap permintaan berdiri sendiri. Ini bukan pilihan gaya:
                // saat mengevaluasi model, tiap kalimat uji dijalankan dengan
                // reset dulu (stateless=True di vm/eval_app.py). Tanpa reset,
                // kalimat kedua menempel ke kalimat pertama dan akurasinya
                // turun jauh dari angka eval.
                NeedleNative.reset()
                NeedleNative.complete(text, maxNewTokens)
                    ?: throw IllegalStateException(
                        NeedleNative.lastError() ?: "Otak lokal gagal menjawab."
                    )
            }
        }

    private fun ensureReady() {
        if (!modelLoaded) {
            // ponytail: model dibaca utuh ke heap (63 MB) lalu disalin sekali lagi
            // oleh mesinnya, jadi puncak pemakaian ±130 MB. Aman di HP sekarang,
            // tapi kalau nanti ada laporan mati mendadak di HP RAM kecil,
            // jalan keluarnya: salin aset ke filesDir sekali, lalu mmap berkas
            // itu dan kirim alamatnya (bukan isinya) ke needle_load.
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            if (NeedleNative.loadModel(bytes) < 0) {
                throw IllegalStateException(
                    NeedleNative.lastError() ?: "Model lokal gagal dimuat."
                )
            }
            modelLoaded = true
        }

        // Prefix dihitung ulang saat baris tanggalnya berubah (ganti hari).
        val system = systemPrompt(clock())
        if (system != preparedPrefix) {
            if (NeedleNative.initModel(system, TOOLS_JSON) < 0) {
                throw IllegalStateException(
                    NeedleNative.lastError() ?: "Model lokal gagal disiapkan."
                )
            }
            preparedPrefix = system
        }
    }

    internal companion object {
        /** Nama file model di app/src/main/assets. */
        const val MODEL_ASSET = "rank64.cact"

        /**
         * Daftar alat yang dikenal model -- HARUS sama dengan data_app/tools.json
         * yang dipakai saat melatih dan mengevaluasi. Mengubahnya berarti model
         * menerima prefix yang tidak pernah ia lihat.
         */
        const val TOOLS_JSON =
            """[{"name":"catat_pengeluaran","parameters":{"type":"object","properties":{"amount":{"type":"integer"},"category":{"type":"string"},"item":{"type":"string"},"date":{"type":"string"}},"required":["amount","category"]}},""" +
                """{"name":"catat_masuk","parameters":{"type":"object","properties":{"amount":{"type":"integer"},"category":{"type":"string"},"item":{"type":"string"},"date":{"type":"string"}},"required":["amount","category"]}},""" +
                """{"name":"tanya","parameters":{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}}]"""

        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        private val DAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        /** Format latih: "date: 2026-03-11 Wed 07:55". */
        internal fun systemPrompt(nowMs: Long): String {
            val zoned = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
            return "date: ${zoned.format(DATE)} ${zoned.format(DAY)} ${zoned.format(TIME)}"
        }
    }
}
