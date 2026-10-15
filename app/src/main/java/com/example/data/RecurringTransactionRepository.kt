package com.example.data

import android.util.Log
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

    /**
     * H1: tandai SYNCED hanya untuk baris yang updatedAt-nya belum berubah sejak push.
     */
    suspend fun markSyncedIfUnchanged(items: List<Pair<String, Long>>) {
        items.forEach { (syncKey, pushedUpdatedAt) ->
            dao.markSyncedIfUnchanged(syncKey, pushedUpdatedAt)
        }
    }

    /**
     * Menerapkan versi remote; dicocokkan lewat syncKey, updatedAt remote dipertahankan.
     * H8: baris cloud yang rusak divalidasi dulu; yang tidak layak dilewati.
     * Mengembalikan true bila baris benar-benar diterapkan.
     */
    suspend fun applyRemote(remote: RecurringTransaction): Boolean {
        if (remote.syncKey.isBlank()) {
            Log.w(TAG, "Recurring remote tanpa syncKey dilewati.")
            return false
        }
        val normalized = remote.copy(type = FinancialRecordRepository.normalizeType(remote.type))
        val valid = normalized.description.isNotBlank() &&
            normalized.description.length <= FinancialRecordRepository.MAX_DESC_LENGTH &&
            normalized.amount > 0L &&
            normalized.amount <= FinancialRecordRepository.MAX_AMOUNT &&
            normalized.type in TYPE_SET &&
            normalized.category.isNotBlank() &&
            normalized.category.length <= FinancialRecordRepository.MAX_CATEGORY_LENGTH &&
            normalized.dayOfMonth in 1..31
        if (!valid) {
            Log.w(TAG, "Recurring remote tidak valid dilewati (syncKey='${remote.syncKey}').")
            return false
        }

        val existing = dao.findBySyncKey(normalized.syncKey)
        if (existing == null) {
            dao.insertRecurring(normalized)
        } else {
            dao.updateRecurring(normalized.copy(id = existing.id))
        }
        return true
    }

    companion object {
        private const val TAG = "RecurringTransactionRepository"
        private val TYPE_SET = setOf("income", "expense")
    }
}