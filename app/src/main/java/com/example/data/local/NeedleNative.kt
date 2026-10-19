package com.example.data.local

/**
 * Pintu masuk ke mesin teks lokal (libneedle_jni.so, lihat build_android/jni).
 *
 * Mesinnya satu untuk seluruh proses dan TIDAK thread-safe, jadi semua panggilan
 * harus lewat [NeedleLocalEngine] yang menyerialkannya -- jangan panggil langsung
 * dari beberapa coroutine.
 *
 * Penjelasan tiap fungsi ada di needle_jni.c; semuanya mengembalikan nilai
 * negatif saat gagal, dan alasan kegagalannya dibaca lewat [lastError].
 */
internal object NeedleNative {

    init {
        System.loadLibrary("needle_jni")
    }

    /** Mengembalikan 0 atau lebih saat berhasil, negatif saat gagal. */
    external fun loadModel(cact: ByteArray): Int

    /** Mengembalikan panjang prefix (jumlah token) saat berhasil, negatif saat gagal. */
    external fun initModel(system: String, tools: String): Int

    /** Balasan JSON mesin, atau null saat gagal. */
    external fun complete(text: String, maxNewTokens: Int): String?

    external fun reset()

    external fun lastError(): String?

    external fun models(): Int
}
