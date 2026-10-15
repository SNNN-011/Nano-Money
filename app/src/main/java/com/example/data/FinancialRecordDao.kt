package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Dao
interface FinancialRecordDao {
    @Query("SELECT * FROM financial_records WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllRecords(): Flow<List<FinancialRecord>>

    @Query("SELECT * FROM financial_records ORDER BY date DESC")
    fun getAllRecordsWithDeleted(): Flow<List<FinancialRecord>>

    // Sampah: record yang sudah dihapus tapi masih bisa dipulihkan.
    @Query("SELECT * FROM financial_records WHERE isDeleted = 1 ORDER BY date DESC")
    fun getDeletedRecords(): Flow<List<FinancialRecord>>

    // Kembalikan record dari sampah ke daftar utama (perubahan lokal -> PENDING).
    @Query("UPDATE financial_records SET isDeleted = 0, updatedAt = :now, syncStatus = 1 WHERE id IN (:ids)")
    suspend fun restoreRecords(ids: List<Int>, now: Long)

    // Hapus permanen: ikut dibersihkan dari cloud lewat purgeRecordsFromFirestore.
    @Query("DELETE FROM financial_records WHERE isDeleted = 1")
    suspend fun purgeDeletedRecords()

    @Query("SELECT * FROM financial_records WHERE date >= :startMs AND date <= :endMs AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsInTimeRange(startMs: Long, endMs: Long): Flow<List<FinancialRecord>>

    fun getTransactionsByMonth(year: Int, month: Int): Flow<List<FinancialRecord>> {
        val yearMonth = YearMonth.of(year, month)
        val zoneId = ZoneId.systemDefault()
        val startMs = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return getTransactionsInTimeRange(startMs, endMs)
    }

    fun getTransactionsByDate(date: LocalDate): Flow<List<FinancialRecord>> {
        val zoneId = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return getTransactionsInTimeRange(startMs, endMs)
    }

    @Query("SELECT * FROM financial_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): FinancialRecord?

    @Query("SELECT COUNT(*) FROM financial_records WHERE isDeleted = 0")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: FinancialRecord): Long

    @Update
    suspend fun updateRecord(record: FinancialRecord)

    // Hapus semua transaksi yang masih aktif (tidak termasuk isi Sampah).
    @Query("UPDATE financial_records SET isDeleted = 1, updatedAt = :now, syncStatus = 1 WHERE isDeleted = 0")
    suspend fun softDeleteAllActiveRecords(now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<FinancialRecord>)

    // ---- Jalur sinkronisasi ----

    @Query("SELECT * FROM financial_records WHERE syncKey = :syncKey LIMIT 1")
    suspend fun findBySyncKey(syncKey: String): FinancialRecord?

    @Query("SELECT * FROM financial_records WHERE syncStatus = 1")
    suspend fun getPendingSync(): List<FinancialRecord>

    @Query("UPDATE financial_records SET syncStatus = 0 WHERE syncKey IN (:syncKeys)")
    suspend fun markSynced(syncKeys: List<String>)

    /**
     * H1: hanya tandai SYNCED bila baris belum berubah sejak dibaca untuk push.
     * Kalau user mengedit di tengah push, updatedAt sudah bergerak dan baris tetap
     * PENDING supaya edit barunya ikut terkirim di push berikutnya.
     */
    @Query("UPDATE financial_records SET syncStatus = 0 WHERE syncKey = :syncKey AND updatedAt = :pushedUpdatedAt")
    suspend fun markSyncedIfUnchanged(syncKey: String, pushedUpdatedAt: Long)

    /** Ganti akun (K5): kosongkan tabel; hanya isi yang dibuang, bukan skema/DB-nya. */
    @Query("DELETE FROM financial_records")
    suspend fun deleteAllHard()

    /** Restore backup: seluruh baris dianggap perubahan terbaru (menang atas versi cloud). */
    @Query("UPDATE financial_records SET syncStatus = 1, updatedAt = :now")
    suspend fun markAllPendingForRestore(now: Long)

    /** Rename kategori ikut mengubah nama yang tersimpan di transaksi (categoryId tetap). */
    @Query(
        "UPDATE financial_records SET category = :newName, updatedAt = :now, syncStatus = 1 " +
            "WHERE categoryId = :categoryId AND isDeleted = 0"
    )
    suspend fun renameCategoryOnRecords(categoryId: String, newName: String, now: Long): Int

    /** Kategori dihapus: transaksinya pindah ke kategori pengganti (Lainnya). */
    @Query(
        "UPDATE financial_records SET categoryId = :newCategoryId, category = :newName, " +
            "updatedAt = :now, syncStatus = 1 WHERE categoryId = :oldCategoryId AND isDeleted = 0"
    )
    suspend fun moveRecordsToCategory(
        oldCategoryId: String,
        newCategoryId: String,
        newName: String,
        now: Long
    ): Int

    // ---- Backfill referensi kategori ----

    /**
     * Transaksi hasil migrasi lama belum punya categoryId, padahal nama kategorinya
     * masih tersimpan. Baris seperti itu dikumpulkan supaya bisa diisi id-nya.
     */
    @Query("SELECT * FROM financial_records WHERE categoryId = ''")
    suspend fun getRecordsWithoutCategoryId(): List<FinancialRecord>

    @Query("UPDATE financial_records SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategoryId(id: Int, categoryId: String)

    /**
     * M20: isi categoryId satu statement SQL, bukan satu UPDATE per baris.
     * Cocokkan lewat type + nama (case-insensitive) ke kategori aktif.
     * Mengembalikan jumlah baris yang benar-benar terisi.
     */
    @Query(
        "UPDATE financial_records SET categoryId = (" +
            "SELECT c.id FROM categories c WHERE c.type = financial_records.type " +
            "AND c.name = financial_records.category COLLATE NOCASE AND c.isDeleted = 0 LIMIT 1" +
            ") WHERE categoryId = '' AND EXISTS (" +
            "SELECT 1 FROM categories c WHERE c.type = financial_records.type " +
            "AND c.name = financial_records.category COLLATE NOCASE AND c.isDeleted = 0)"
    )
    suspend fun backfillCategoryIdsFromCategories(): Int
}