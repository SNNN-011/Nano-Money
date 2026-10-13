package com.example.util

import android.content.Context
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Penanda hasil restore backup.
 *
 * Restore menimpa database lokal dengan versi lama. Supaya hasil restore tidak kalah
 * oleh versi di Firestore, seluruh baris ditandai PENDING + updatedAt = sekarang pada
 * start berikutnya, lalu penanda dibersihkan.
 *
 * Penanda ditaruh di SharedPreferences biasa (bukan encrypted) karena hanya berisi
 * boolean dan harus terbaca meski keystore bermasalah.
 */
object RestoreSyncMarker {

    private const val PREFS_NAME = "sync_meta"
    private const val KEY_RESTORE_PENDING = "restore_pending"

    fun markPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTORE_PENDING, true)
            .apply()
    }

    /** Dipanggil sekali saat app start. Tidak melakukan apa-apa kalau tak ada restore. */
    suspend fun consumeIfPresent(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_PENDING, false)) return

        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val db = AppDatabase.getDatabase(context)
                db.financialRecordDao().markAllPendingForRestore(now)
                db.recurringTransactionDao().markAllPendingForRestore(now)
            } finally {
                prefs.edit().remove(KEY_RESTORE_PENDING).apply()
            }
        }
    }
}