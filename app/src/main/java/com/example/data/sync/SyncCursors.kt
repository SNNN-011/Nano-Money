package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpan kursor waktu sinkron per koleksi, uid akun terakhir, dan tombstone.
 *
 * Sengaja memakai SharedPreferences biasa (bukan encrypted): isinya hanya angka/id,
 * dan state ini murni lokal perangkat — tidak boleh ikut terbackup ke cloud.
 */
object SyncCursors {

    private const val PREFS_NAME = "sync_meta"
    private const val KEY_PREFIX = "last_sync_"
    private const val KEY_LAST_UID = "last_synced_uid"

    /** Tombstone (id dokumen yang terhapus di cloud) hidup 30 hari saja. */
    const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, collection: String): Long =
        prefs(context).getLong(KEY_PREFIX + collection, 0L)

    fun set(context: Context, collection: String, updatedAt: Long) {
        prefs(context).edit()
            .putLong(KEY_PREFIX + collection, updatedAt)
            .apply()
    }

    fun isAdopted(context: Context, collection: String): Boolean =
        prefs(context).getBoolean(KEY_PREFIX + collection + "_adopted", false)

    fun markAdopted(context: Context, collection: String) {
        prefs(context).edit()
            .putBoolean(KEY_PREFIX + collection + "_adopted", true)
            .apply()
    }

    // ---- Keamanan ganti akun (K5) ----

    /** uid yang terakhir dipakai sync di perangkat ini; null berarti belum pernah. */
    fun getLastUid(context: Context): String? =
        prefs(context).getString(KEY_LAST_UID, null)?.takeIf { it.isNotBlank() }

    /** Disimpan sinkron (commit) supaya uid baru pasti tercatat sebelum push pertama. */
    fun setLastUid(context: Context, uid: String) {
        prefs(context).edit().putString(KEY_LAST_UID, uid).commit()
    }

    /**
     * Menghapus seluruh state sync lokal (kursor, flag adopsi, tombstone, uid).
     * Dipakai saat ganti akun: tanpa ini, kursor milik akun lama menyembunyikan
     * dokumen akun baru, dan flag adopsi lama melewati adopsi yang seharusnya jalan.
     */
    fun resetForAccountChange(context: Context) {
        prefs(context).edit().clear().commit()
    }

    // ---- Tombstone penghapusan cloud (M4) ----
    // Format entri: "<expiryMs>|<docId>", disimpan sebagai StringSet per koleksi.

    private fun tombstoneKey(collection: String) = KEY_PREFIX + "tombstone_" + collection

    private fun entries(context: Context, collection: String): Set<String> =
        prefs(context).getStringSet(tombstoneKey(collection), emptySet())?.toSet() ?: emptySet()

    /** Menambahkan id dokumen yang diketahui terhapus dari cloud, sekaligus membuang yang kedaluwarsa. */
    fun addTombstones(context: Context, collection: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val merged = entries(context, collection)
            .filterTo(mutableSetOf()) { entry -> (entry.substringBefore('|').toLongOrNull() ?: 0L) > now }
        ids.forEach { id ->
            if (id.isNotBlank()) merged += "${now + TOMBSTONE_TTL_MS}|$id"
        }
        prefs(context).edit().putStringSet(tombstoneKey(collection), merged).apply()
    }

    /** Id dokumen yang masih bertombstone; dokumen seperti ini tidak boleh dikirim ulang/ditarik. */
    fun getTombstones(context: Context, collection: String): Set<String> {
        val now = System.currentTimeMillis()
        return entries(context, collection)
            .filter { entry -> (entry.substringBefore('|').toLongOrNull() ?: 0L) > now }
            .map { entry -> entry.substringAfter('|', "") }
            .filterTo(mutableSetOf()) { id -> id.isNotBlank() }
    }
}
