package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

@Dao
interface FinancialRecordDao {
    @Query("SELECT * FROM financial_records WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllRecords(): Flow<List<FinancialRecord>>

    @Query("SELECT * FROM financial_records ORDER BY date DESC")
    fun getAllRecordsWithDeleted(): Flow<List<FinancialRecord>>

    // Sampah: record yang sudah dihapus tapi masih bisa dipulihkan.
    @Query("SELECT * FROM financial_records WHERE isDeleted = 1 ORDER BY date DESC")
    fun getDeletedRecords(): Flow<List<FinancialRecord>>

    // Kembalikan record dari sampah ke daftar utama.
    @Query("UPDATE financial_records SET isDeleted = 0 WHERE id IN (:ids)")
    suspend fun restoreRecords(ids: List<Int>)

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

    @Query("SELECT COUNT(*) FROM financial_records WHERE isDeleted = 0")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: FinancialRecord): Long

    @Update
    suspend fun updateRecord(record: FinancialRecord)

    @Delete
    suspend fun deleteRecord(record: FinancialRecord)

    @Query("DELETE FROM financial_records")
    suspend fun deleteAllRecords()

    // Hapus semua transaksi yang masih aktif (tidak termasuk isi Sampah).
    @Query("UPDATE financial_records SET isDeleted = 1 WHERE isDeleted = 0")
    suspend fun softDeleteAllActiveRecords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<FinancialRecord>)

    @Transaction
    suspend fun replaceAllRecords(records: List<FinancialRecord>) {
        deleteAllRecords()
        insertAll(records)
    }
}
