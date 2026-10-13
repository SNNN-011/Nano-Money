package com.example.domain

import kotlinx.coroutines.TimeoutCancellationException
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
