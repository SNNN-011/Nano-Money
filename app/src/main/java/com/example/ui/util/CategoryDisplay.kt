package com.example.ui.util

import com.example.data.FinancialRecord

/**
 * Nama kategori yang ditampilkan untuk sebuah transaksi.
 *
 * Transaksi menyimpan nama kategori saat dibuat (cache historis) sekaligus categoryId.
 * Kalau kategori sudah di-rename, hasil terbaru yang dipakai supaya tampilan ikut
 * berubah; bila id-nya belum dikenal (data lama), jatuh ke cache.
 */
fun FinancialRecord.displayCategoryName(categoryNameById: Map<String, String>): String =
    if (categoryId.isNotEmpty()) categoryNameById[categoryId] ?: category else category