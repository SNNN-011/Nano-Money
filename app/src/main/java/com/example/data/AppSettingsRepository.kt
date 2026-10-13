package com.example.data

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.Flow

/**
 * Setting kecil yang ikut tersinkron (lihat [SyncableSettings]).
 *
 * Hanya key yang ada di allow-list yang boleh lewat sini. PIN, biometrik, dan
 * preferensi khusus perangkat tetap tinggal di EncryptedSharedPreferences.
 */
class AppSettingsRepository(private val dao: AppSettingDao) {

    fun observe(key: String): Flow<AppSetting?> = dao.observe(key)

    suspend fun find(key: String): AppSetting? = dao.find(key)

    suspend fun getLong(key: String, default: Long = 0L): Long =
        dao.find(key)?.valueLong ?: default

    suspend fun setLong(key: String, value: Long) {
        require(SyncableSettings.isSyncable(key)) { "Key '$key' tidak boleh disinkronkan." }
        dao.upsert(
            AppSetting(
                key = key,
                valueLong = value,
                updatedAt = System.currentTimeMillis(),
                syncKey = key,
                syncStatus = SyncStatus.PENDING
            )
        )
    }

    // ---- Jalur sinkronisasi ----

    /**
     * Hanya key yang ada di allow-list yang ikut antrean sync.
     *
     * Baris di luar allow-list bisa saja ada dari versi lama, jadi penyaringan
     * dilakukan saat dibaca, bukan hanya saat menulis. Kalau tidak, pin/biometrik
     * yang terlanjur tersimpan akan ikut terkirim ke Firestore.
     */
    suspend fun getPendingSync(): List<AppSetting> =
        dao.getPendingSync().filter { SyncableSettings.isSyncable(it.key) }

    suspend fun markSynced(syncKeys: List<String>) = dao.markSynced(syncKeys)

    /** Menerapkan versi remote; key terlarang diabaikan. updatedAt remote dipertahankan. */
    suspend fun applyRemote(remote: AppSetting) {
        if (!SyncableSettings.isSyncable(remote.key)) {
            Log.w("AppSettingsRepository", "Key '${remote.key}' di luar allow-list, diabaikan.")
            return
        }
        dao.upsertRemote(listOf(remote))
    }

    companion object {
        private const val TAG = "SettingsMigration"
        const val PREF_MIGRATED_FLAG = "settings_migrated_v1"

        /**
         * Migrasi sekali dari prefs lama ke tabel app_settings. Idempoten lewat flag.
         * Hanya key yang ada di allow-list yang dipindahkan.
         */
        suspend fun runIfNeeded(dao: AppSettingDao, prefs: SharedPreferences): Boolean {
            if (prefs.getBoolean(PREF_MIGRATED_FLAG, false)) return false

            val now = System.currentTimeMillis()
            val keys = SyncableSettings.allowListed(prefs.all.keys)
            if (keys.isEmpty()) {
                prefs.edit().putBoolean(PREF_MIGRATED_FLAG, true).apply()
                return false
            }

            val settings = keys.mapNotNull { key ->
                when (val raw = prefs.all[key]) {
                    is Long -> AppSetting(key, null, raw, null, now, key, SyncStatus.PENDING)
                    is Int -> AppSetting(key, null, raw.toLong(), null, now, key, SyncStatus.PENDING)
                    is String -> AppSetting(key, raw, null, null, now, key, SyncStatus.PENDING)
                    is Boolean -> AppSetting(key, null, null, raw, now, key, SyncStatus.PENDING)
                    else -> null.also { Log.w(TAG, "Tipe tidak didukung untuk '$key', dilewati") }
                }
            }

            if (settings.isNotEmpty()) dao.upsertAll(settings)
            prefs.edit().putBoolean(PREF_MIGRATED_FLAG, true).apply()
            return settings.isNotEmpty()
        }
    }
}