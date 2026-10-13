package com.example.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Satu-satunya pintu tulis transaksi ke Room.
 *
 * Semua tulis lokal WAJIB membubuhkan updatedAt = sekarang dan syncStatus = PENDING
 * agar engine sync tahu baris ini perlu dikirim. Data dari remote masuk lewat
 * applyRemote() yang tidak menyentuh updatedAt lokal.
 *
 * [categoryIdResolver] dipakai untuk mengisi categoryId dari nama kategori yang masih
 * dipakai form/parser, sehingga semua titik tulis punya referensi kategori.
 */
class FinancialRecordRepository(
    private val dao: FinancialRecordDao,
    private val categoryIdResolver: suspend (type: String, name: String) -> String = { _, _ -> "" }
) {

    val allRecords: Flow<List<FinancialRecord>> = dao.getAllRecords()

    companion object {
        const val MAX_AMOUNT = 999_999_999_999L // 1 Triliun Rupiah
        const val MAX_DESC_LENGTH = 500
        const val MAX_CATEGORY_LENGTH = 100
    }

    private fun validateRecord(record: FinancialRecord) {
        require(record.description.isNotBlank()) { "Deskripsi tidak boleh kosong" }
        require(record.description.length <= MAX_DESC_LENGTH) { "Deskripsi tidak boleh melebihi $MAX_DESC_LENGTH karakter" }
        require(record.amount > 0L) { "Jumlah transaksi harus lebih dari 0" }
        require(record.amount <= MAX_AMOUNT) { "Jumlah transaksi melebihi batas maksimum" }
        val typeLower = record.type.lowercase()
        require(typeLower in setOf("income", "expense", "pemasukan", "pengeluaran")) {
            "Tipe transaksi tidak valid"
        }
        require(record.category.isNotBlank()) { "Kategori tidak boleh kosong" }
        require(record.category.length <= MAX_CATEGORY_LENGTH) { "Kategori tidak boleh melebihi $MAX_CATEGORY_LENGTH karakter" }
    }

    suspend fun getRecordCount(): Int = dao.getRecordCount()

    suspend fun insert(record: FinancialRecord): Long {
        validateRecord(record)
        val stamped = record.copy(
            syncKey = record.syncKey.ifBlank { SyncKeys.newTransactionKey() },
            categoryId = record.categoryId.ifBlank { resolveCategoryId(record.type, record.category) },
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        return dao.insertRecord(stamped)
    }

    suspend fun insertAll(records: List<FinancialRecord>) {
        val now = System.currentTimeMillis()
        dao.insertAll(
            records.map {
                validateRecord(it)
                it.copy(
                    syncKey = SyncKeys.newTransactionKey(),
                    categoryId = it.categoryId.ifBlank { resolveCategoryId(it.type, it.category) },
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING
                )
            }
        )
    }

    suspend fun update(record: FinancialRecord) {
        validateRecord(record)
        dao.updateRecord(
            record.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = record.syncKey.ifBlank { SyncKeys.newTransactionKey() }
            )
        )
    }

    /** Soft delete: baris tetap ada agar aksi hapus bisa disinkronkan & dipulihkan dari Sampah. */
    suspend fun delete(record: FinancialRecord) {
        dao.updateRecord(
            record.copy(
                isDeleted = true,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = record.syncKey.ifBlank { SyncKeys.newTransactionKey() }
            )
        )
    }

    fun getDeletedRecords(): Flow<List<FinancialRecord>> = dao.getDeletedRecords()

    suspend fun restore(records: List<FinancialRecord>) =
        dao.restoreRecords(records.map { it.id }, System.currentTimeMillis())

    suspend fun restore(record: FinancialRecord) = dao.restoreRecords(listOf(record.id), System.currentTimeMillis())

    suspend fun emptyTrash() = dao.purgeDeletedRecords()

    /**
     * Pindahkan semua transaksi aktif ke Sampah (bukan hapus permanen),
     * supaya masih bisa dipulihkan. Isi Sampah tidak ikut terpengaruh.
     */
    suspend fun moveAllActiveToTrash() = dao.softDeleteAllActiveRecords(System.currentTimeMillis())

    suspend fun replaceAll(records: List<FinancialRecord>) {
        records.forEach { validateRecord(it) }
        val now = System.currentTimeMillis()
        dao.replaceAllRecords(
            records.map {
                it.copy(
                    syncKey = SyncKeys.newTransactionKey(),
                    categoryId = it.categoryId.ifBlank { resolveCategoryId(it.type, it.category) },
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING
                )
            }
        )
    }

    // ---- Jalur sinkronisasi ----

    suspend fun findBySyncKey(syncKey: String): FinancialRecord? = dao.findBySyncKey(syncKey)

    suspend fun getPendingSync(): List<FinancialRecord> = dao.getPendingSync()

    suspend fun markSynced(syncKeys: List<String>) = dao.markSynced(syncKeys)

    /**
     * Menerapkan versi remote. Baris lokal dicocokkan lewat syncKey (bukan id lokal),
     * jadi id Room milik masing-masing perangkat tidak pernah saling menimpa.
     * updatedAt milik remote dipertahankan.
     */
    suspend fun applyRemote(remote: FinancialRecord) {
        val existing = dao.findBySyncKey(remote.syncKey)
        if (existing == null) {
            dao.insertRecord(remote)
        } else {
            dao.updateRecord(remote.copy(id = existing.id))
        }
    }

    private suspend fun resolveCategoryId(type: String, name: String): String =
        runCatching { categoryIdResolver(type, name) }.getOrDefault("")

    fun getTransactionsByMonth(year: Int, month: Int): Flow<List<FinancialRecord>> = dao.getTransactionsByMonth(year, month)

    fun getTransactionsByDate(date: LocalDate): Flow<List<FinancialRecord>> = dao.getTransactionsByDate(date)
}