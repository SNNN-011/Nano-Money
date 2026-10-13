package com.example.data.sync

/**
 * Aturan konflik satu-satunya untuk seluruh entitas yang disinkronkan.
 *
 * Versi yang menang ditentukan waktu, bukan device mana yang kebetulan sync
 * terakhir. Versi remote hanya dipakai kalau benar-benar lebih baru.
 */
object ConflictResolver {

    /**
     * @param localUpdatedAt waktu edit lokal terakhir, atau null kalau baris belum ada.
     * @param remoteUpdatedAt waktu edit pada versi cloud.
     */
    fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
}