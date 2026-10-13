package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    /** [isDeleted = 0] wajib: recurring yang dihapus user adalah soft delete. */
    @Query("SELECT * FROM recurring_transactions WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllRecurring(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND isDeleted = 0")
    suspend fun getActiveRecurring(): List<RecurringTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransaction): Long

    @Update
    suspend fun updateRecurring(recurring: RecurringTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurringTransactions: List<RecurringTransaction>)

    // ---- Jalur sinkronisasi ----

    @Query("SELECT * FROM recurring_transactions WHERE syncKey = :syncKey LIMIT 1")
    suspend fun findBySyncKey(syncKey: String): RecurringTransaction?

    @Query("SELECT * FROM recurring_transactions WHERE syncStatus = 1")
    suspend fun getPendingSync(): List<RecurringTransaction>

    @Query("UPDATE recurring_transactions SET syncStatus = 0 WHERE syncKey IN (:syncKeys)")
    suspend fun markSynced(syncKeys: List<String>)

    /** Restore backup: seluruh baris dianggap perubahan terbaru (menang atas versi cloud). */
    @Query("UPDATE recurring_transactions SET syncStatus = 1, updatedAt = :now")
    suspend fun markAllPendingForRestore(now: Long)
}