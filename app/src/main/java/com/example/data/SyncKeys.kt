package com.example.data

import java.time.YearMonth
import java.util.UUID

/**
 * Pembuat syncKey (id dokumen Firestore sekaligus kunci unik lokal).
 *
 * ID autoincrement Room TIDAK boleh dipakai sebagai kunci sinkronisasi karena
 * dua perangkat bisa menghasilkan id lokal yang sama untuk transaksi berbeda.
 */
object SyncKeys {

    /** Kunci untuk transaksi manual: acak, satu transaksi satu kunci. */
    fun newTransactionKey(): String = "t:" + UUID.randomUUID()

    /** Kunci untuk kategori buatan user: acak. Migrasi awal memakai kunci deterministik. */
    fun newCategoryKey(): String = "cat:" + UUID.randomUUID()

    /** Kunci untuk recurring buatan user: acak. Migrasi awal memakai 'r:legacy:<rowId>'. */
    fun newRecurringKey(): String = "r:" + UUID.randomUUID()

    /**
     * Kunci untuk transaksi hasil generate otomatis dari recurring.
     * Deterministik agar two device yang memproses recurring sama tidak menghasilkan
     * dua transaksi ganda untuk bulan yang sama.
     */
    fun recurringGenerated(recurringKey: String, yearMonth: YearMonth): String =
        "r:$recurringKey:$yearMonth"

    fun recurringGenerated(recurring: RecurringTransaction, yearMonth: YearMonth): String =
        recurringGenerated(recurring.syncKey, yearMonth)
}