package com.example.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Mode impor baris dari file Excel/CSV.
 *
 * [MERGE] menambahkan baris baru tanpa menyentuh data lama, [REPLACE] memindahkan
 * data aktif ke Sampah (soft delete, masih bisa dipulihkan) lalu memasukkan baris
 * impor. Tidak ada mode yang menghapus data secara permanen.
 */
enum class ImportMode { MERGE, REPLACE }

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

        /**
         * Repository transaksi yang categoryId-nya selalu terisi.
         *
         * Dipakai semua titik ViewModel supaya tidak ada jalur tulis yang menghasilkan
         * transaksi tanpa referensi kategori. Kalau ada, rename dan hapus kategori tidak
         * akan berefek ke transaksi itu.
         */
        fun create(db: AppDatabase): FinancialRecordRepository =
            FinancialRecordRepository(db.financialRecordDao()) { type, name ->
                db.categoryDao().findIdByName(type, name).orEmpty()
            }
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

    /**
     * Mengubah transaksi yang sudah ada.
     *
     * Form hanya mengirim id + isi baru, jadi `syncKey` dan status Sampah diambil
     * kembali dari baris lama. Tanpa itu, edit akan membuat syncKey baru: dokumen
     * cloud yang lama tidak pernah terpakai lagi dan akan muncul balik saat pull,
     * sedangkan transaksi hasil edit tersimpan sebagai duplikat.
     */
    suspend fun update(record: FinancialRecord) {
        validateRecord(record)
        val existing = dao.findById(record.id)
        dao.updateRecord(
            record.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = existing?.syncKey?.takeIf { it.isNotBlank() } ?: SyncKeys.newTransactionKey(),
                isDeleted = existing?.isDeleted ?: record.isDeleted,
                // Kalau user mengganti kategori, categoryId lama harus ditulis ulang;
                // kalau tidak, transaksi masih menunjuk ke kategori yang tidak dipilih.
                categoryId = record.categoryId.ifBlank { resolveCategoryId(record.type, record.category) }
            )
        )
    }

    /** Soft delete: baris tetap ada agar aksi hapus bisa disinkronkan & dipulihkan dari Sampah. */
    suspend fun delete(record: FinancialRecord) {
        val existing = dao.findById(record.id)
        dao.updateRecord(
            record.copy(
                isDeleted = true,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                syncKey = existing?.syncKey?.takeIf { it.isNotBlank() }
                    ?: record.syncKey.ifBlank { SyncKeys.newTransactionKey() }
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

    /**
     * Gabungkan baris hasil impor dengan data yang sudah ada.
     * Baris lama tidak disentuh sama sekali.
     */
    suspend fun mergeAll(records: List<FinancialRecord>) = insertImported(records)

    /**
     * Ganti seluruh data aktif dengan baris hasil impor.
     *
     * Baris lama TIDAK dihapus permanen: dipindah ke Sampah supaya masih bisa
     * dipulihkan, dan isi Sampah yang lama tidak ikut terhapus. Baris impor selalu
     * dapat syncKey baru sehingga terkirim ke cloud, sedangkan dokumen cloud milik
     * data lama ditutup oleh status isDeleted = 1.
     */
    suspend fun replaceAllSoft(records: List<FinancialRecord>) {
        moveAllActiveToTrash()
        insertImported(records)
    }

    /** Validasi + stempel baris impor (syncKey/categoryId/updatedAt/syncStatus). */
    private suspend fun insertImported(records: List<FinancialRecord>) {
        records.forEach { validateRecord(it) }
        val now = System.currentTimeMillis()
        dao.insertAll(
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

    /**
     * Alias lama untuk [replaceAllSoft] agar pemanggil yang sudah ada (impor JSON)
     * tetap berjalan. Sejak perbaikan ini alias ini juga tidak pernah hard delete.
     */
    @Deprecated("Gunakan mergeAll() atau replaceAllSoft() supaya mode impor eksplisit.")
    suspend fun replaceAll(records: List<FinancialRecord>) = replaceAllSoft(records)

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

    /**
     * Mengisi categoryId pada transaksi lama yang masih kosong.
     *
     * Transaksi hasil migrasi v5 hanya punya nama kategori, sedangkan kategori kini
     * punya id stabil. Tanpa pengisian ini, rename/hapus kategori tidak bisa efek ke
     * transaksi lama. Idempoten: hanya baris dengan categoryId kosong yang disentuh.
     */
    suspend fun backfillCategoryIds(): Int {
        val orphans = dao.getRecordsWithoutCategoryId()
        var filled = 0
        orphans.forEach { record ->
            val id = resolveCategoryId(record.type, record.category)
            if (id.isNotBlank()) {
                dao.setCategoryId(record.id, id)
                filled++
            }
        }
        return filled
    }

    fun getTransactionsByMonth(year: Int, month: Int): Flow<List<FinancialRecord>> = dao.getTransactionsByMonth(year, month)

    fun getTransactionsByDate(date: LocalDate): Flow<List<FinancialRecord>> = dao.getTransactionsByDate(date)
}