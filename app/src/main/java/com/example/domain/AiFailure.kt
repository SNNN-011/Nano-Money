package com.example.domain

import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Menerjemahkan kegagalan jaringan/API menjadi pesan yang bisa dimengerti user.
 *
 * Sebelumnya semua kegagalan jadi "API tidak merespons", sehingga timeout, model
 * yang ditolak proxy, dan rate limit terlihat sama saja.
 */
internal fun describeAiFailure(e: Throwable?): String = when {
    e == null -> "Server AI tidak merespons."
    e is TimeoutCancellationException -> "Server AI terlalu lama merespons. Coba lagi sebentar."
    e is HttpException -> when (e.code()) {
        401 -> "Sesi login bermasalah, silakan coba lagi."
        403 -> "Server AI menolak permintaan ini. Periksa konfigurasi model."
        404 -> "Model AI tidak tersedia. Periksa konfigurasi model."
        429 -> "Terlalu banyak permintaan. Coba beberapa saat lagi."
        in 500..599 -> "Server AI sedang bermasalah. Coba kembali nanti."
        else -> "Server AI menolak permintaan (HTTP ${e.code()})."
    }
    else -> "Gagal menghubungi server AI. Periksa koneksi internet Anda."
}

/** Batas nominal transaksi yang diterima, sama dengan gate validasi di repository. */
internal const val AI_MAX_AMOUNT = 999_999_999_999L

/** Jendela tanggal dari AI: 10 tahun ke belakang. */
internal const val AI_MAX_DATE_PAST_MS = 10L * 365 * 24 * 60 * 60 * 1000

/** Jendela tanggal dari AI: 36 jam ke depan (toleransi zona waktu/format). */
internal const val AI_MAX_DATE_FUTURE_MS = 36L * 60 * 60 * 1000

/**
 * Membaca angka dari JSON hasil AI tanpa memotong desimal seperti `optLong`.
 *
 * Model kadang mengirim `15000.0` atau `"15000"`; nilai yang tidak bisa dibaca
 * mengembalikan [default] supaya pemanggil bisa menolak/menggantinya sendiri.
 */
internal fun JSONObject.optAmountLong(key: String, default: Long): Long {
    val raw = opt(key)
    val value = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.trim().toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }
    if (!value.isFinite()) return default
    return kotlin.math.round(value).toLong()
}

/**
 * Benar bila tanggal dari AI masih masuk akal.
 *
 * Tanpa jendela ini, jawaban seperti `0` atau tahun jauh di masa lalu/lalu bisa
 * membuat transaksi "menyusup" ke bulan yang salah di Beranda/Kalender.
 */
internal fun isPlausibleAiDate(ms: Long, nowMs: Long): Boolean =
    ms in (nowMs - AI_MAX_DATE_PAST_MS)..(nowMs + AI_MAX_DATE_FUTURE_MS)
