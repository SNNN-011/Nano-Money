package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Satu-satunya pintu tulis transaksi berulang ke Room.
 *
 * Penghapusan adalah soft delete (isDeleted = true) supaya aksinya ikut tersinkronkan
 * ke perangkat lain. Tulis lokal selalu membubuhkan updatedAt + syncStatus = PENDING.
 */
class RecurringTransactionRepository(private val dao: RecurringTransactionDao) {

    val allRecurring: Flow<List<RecurringTransaction>> = dao.getAllRecurring()

    suspend fun getActive(): List<RecurringTransaction> = dao.getActiveRecurring()

    suspend fun findBySyncKey(syncKey: String): RecurringTransaction? = dao.findBySyncKey(syncKey)

    suspend fun insert(recurring: RecurringTransaction): Long =
        dao.insertRecurring(
            recurring.copy(
                syncKey = recurring.syncKey.ifBlank { SyncKeys.newRecurringKey() },
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
        )

    suspend fun update(recurring: RecurringTransaction) {
        dao.updateRecurring(
            recurring.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = recurring.syncKey.ifBlank { SyncKeys.newRecurringKey() }
            )
        )
    }

    /** Soft delete. */
    suspend fun softDelete(recurring: RecurringTransaction) {
        dao.updateRecurring(
            recurring.copy(
                isDeleted = true,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = recurring.syncKey.ifBlank { SyncKeys.newRecurringKey() }
            )
        )
    }

    // ---- Jalur sinkronisasi ----

    suspend fun getPendingSync(): List<RecurringTransaction> = dao.getPendingSync()

    suspend fun markSynced(syncKeys: List<String>) = dao.markSynced(syncKeys)

    /** Menerapkan versi remote; dicocokkan lewat syncKey, updatedAt remote dipertahankan. */
    suspend fun applyRemote(remote: RecurringTransaction) {
        val existing = dao.findBySyncKey(remote.syncKey)
        if (existing == null) {
            dao.insertRecurring(remote)
        } else {
            dao.updateRecurring(remote.copy(id = existing.id))
        }
    }
}