package com.example.data.sync

import android.content.Context

/**
 * Penyimpan kursor waktu sinkron per koleksi.
 *
 * Sengaja memakai SharedPreferences biasa (bukan encrypted): isinya hanya angka,
 * dan kursor ini murni state lokal perangkat — tidak boleh ikut terbackup ke cloud.
 */
object SyncCursors {

    private const val PREFS_NAME = "sync_meta"
    private const val KEY_PREFIX = "last_sync_"

    fun get(context: Context, collection: String): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PREFIX + collection, 0L)

    fun set(context: Context, collection: String, updatedAt: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PREFIX + collection, updatedAt)
            .apply()
    }

    fun isAdopted(context: Context, collection: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX + collection + "_adopted", false)

    fun markAdopted(context: Context, collection: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + collection + "_adopted", true)
            .apply()
    }
}