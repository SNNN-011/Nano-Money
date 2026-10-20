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

    /**
     * Daftar kategori yang aktif milik user.
     *
     * Otak v5 (r1..r8) dilatih dengan memilih kategori dari daftar yang
     * DIBERIKAN di `enum` skema tool. Kalau daftar ini tidak ikut dikirim,
     * model menjawab dari ingatan dan kategori benarnya anjlok ke 0,7-4%
     * (lihat vm/CATATAN.md, mode eval `app`). Aplikasi sudah punya daftar ini
     * di database, jadi masalahnya cuma tidak pernah diteruskan ke model.
     *
     * Implementasi yang memang tidak butuh daftar (mis. dummy di test) boleh
     * mengabaikannya.
     */
    suspend fun setCategories(incomeCategories: List<String>, expenseCategories: List<String>) {
    }
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
    private var preparedKey: String? = null
    private var incomeCategories: List<String> = emptyList()
    private var expenseCategories: List<String> = emptyList()

    override suspend fun setCategories(
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ) {
        mutex.withLock {
            this.incomeCategories = incomeCategories
            this.expenseCategories = expenseCategories
        }
    }

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

        // Prefix dihitung ulang saat baris tanggalnya berubah (ganti hari) ATAU
        // saat daftar kategori user berubah. Daftar kategori ikut dikirim karena
        // otak v5 memilih kategori dari daftar yang diberikan.
        val system = systemPrompt(clock())
        val tools = LocalToolsJson.build(incomeCategories, expenseCategories)
        val key = "$system\u0000$tools"
        if (key != preparedKey) {
            if (NeedleNative.initModel(system, tools) < 0) {
                throw IllegalStateException(
                    NeedleNative.lastError() ?: "Model lokal gagal disiapkan."
                )
            }
            preparedKey = key
        }
    }

    internal companion object {
        /** Nama file model di app/src/main/assets. */
        const val MODEL_ASSET = "rank64.cact"

        // Daftar alat tidak lagi konstanta: bentuknya dibangun runtime dari
        // daftar kategori user (lihat [LocalToolsJson]). Bentuk itu HARUS sama
        // dengan yang dipakai saat melatih (v5: dua alat, `category` ber-`enum`,
        // `required` ["category"], pemisah tanpa spasi) -- kalau berbeda, model
        // menerima prefix yang tidak pernah ia lihat dan kategorinya anjlok.

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
