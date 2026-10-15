package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 AND type = :type ORDER BY name ASC")
    fun observeByType(type: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 AND type = :type ORDER BY name ASC")
    suspend fun getByType(type: String): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Category?

    @Query("SELECT id FROM categories")
    suspend fun getAllIds(): List<String>

    @Query("SELECT id FROM categories WHERE type = :type AND name = :name COLLATE NOCASE AND isDeleted = 0 LIMIT 1")
    suspend fun findIdByName(type: String, name: String): String?

    /** M20: pencarian kategori cukup satu baris SQL, bukan memuat semua lalu filter di Kotlin. */
    @Query("SELECT * FROM categories WHERE isDeleted = 0 AND type = :type AND name = :name COLLATE NOCASE ORDER BY name ASC LIMIT 1")
    suspend fun findByTypeAndName(type: String, name: String): Category?

    @Query("SELECT COUNT(*) FROM categories WHERE isDeleted = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE isDeleted = 0 AND type = :type")
    suspend fun countActiveByType(type: String): Int

    @Upsert
    suspend fun upsertAll(categories: List<Category>)

    /** Menerapkan versi remote apa adanya (updatedAt remote tidak ditimpa). */
    @Upsert
    suspend fun upsertRemote(categories: List<Category>)

    @Query("SELECT * FROM categories WHERE syncStatus = 1")
    suspend fun getPendingSync(): List<Category>

    @Query("UPDATE categories SET syncStatus = 0 WHERE syncKey IN (:syncKeys)")
    suspend fun markSynced(syncKeys: List<String>)

    /** H1: tandai SYNCED hanya bila updatedAt baris belum berubah sejak dibaca untuk push. */
    @Query("UPDATE categories SET syncStatus = 0 WHERE syncKey = :syncKey AND updatedAt = :pushedUpdatedAt")
    suspend fun markSyncedIfUnchanged(syncKey: String, pushedUpdatedAt: Long)

    /** Ganti akun (K5): kosongkan tabel; hanya isi yang dibuang, bukan skema/DB-nya. */
    @Query("DELETE FROM categories")
    suspend fun deleteAllHard()

    @Query("UPDATE categories SET syncStatus = 1, updatedAt = :now")
    suspend fun markAllPendingForRestore(now: Long)
}