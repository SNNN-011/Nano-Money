package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun find(key: String): AppSetting?

    @Query("SELECT * FROM app_settings WHERE key IN (:keys)")
    suspend fun find(keys: List<String>): List<AppSetting>

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSetting>>

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    fun observe(key: String): Flow<AppSetting?>

    @Upsert
    suspend fun upsert(setting: AppSetting)

    @Upsert
    suspend fun upsertAll(settings: List<AppSetting>)

    @Upsert
    suspend fun upsertRemote(settings: List<AppSetting>)

    @Query("SELECT * FROM app_settings WHERE syncStatus = 1")
    suspend fun getPendingSync(): List<AppSetting>

    @Query("UPDATE app_settings SET syncStatus = 0 WHERE syncKey IN (:syncKeys)")
    suspend fun markSynced(syncKeys: List<String>)

    @Query("UPDATE app_settings SET syncStatus = 1, updatedAt = :now")
    suspend fun markAllPendingForRestore(now: Long)
}
