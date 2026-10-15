package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Query("SELECT * FROM category_budgets WHERE isDeleted = 0")
    fun observeAll(): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun findByCategoryId(categoryId: String): CategoryBudget?

    @Upsert
    suspend fun upsertAll(budgets: List<CategoryBudget>)

    @Upsert
    suspend fun upsert(budget: CategoryBudget)

    @Upsert
    suspend fun upsertRemote(budgets: List<CategoryBudget>)

    @Query("SELECT * FROM category_budgets WHERE syncStatus = 1")
    suspend fun getPendingSync(): List<CategoryBudget>

    @Query("UPDATE category_budgets SET syncStatus = 0 WHERE syncKey IN (:syncKeys)")
    suspend fun markSynced(syncKeys: List<String>)

    /** H1: tandai SYNCED hanya bila updatedAt baris belum berubah sejak dibaca untuk push. */
    @Query("UPDATE category_budgets SET syncStatus = 0 WHERE syncKey = :syncKey AND updatedAt = :pushedUpdatedAt")
    suspend fun markSyncedIfUnchanged(syncKey: String, pushedUpdatedAt: Long)

    /** Ganti akun (K5): kosongkan tabel; hanya isi yang dibuang, bukan skema/DB-nya. */
    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllHard()

    @Query("UPDATE category_budgets SET syncStatus = 1, updatedAt = :now")
    suspend fun markAllPendingForRestore(now: Long)
}