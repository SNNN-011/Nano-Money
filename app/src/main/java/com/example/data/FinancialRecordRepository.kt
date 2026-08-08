package com.example.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FinancialRecordRepository(private val dao: FinancialRecordDao) {
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
        return dao.insertRecord(record)
    }

    suspend fun update(record: FinancialRecord) {
        validateRecord(record)
        dao.updateRecord(record)
    }

    suspend fun delete(record: FinancialRecord) = dao.updateRecord(record.copy(isDeleted = true))

    suspend fun replaceAll(records: List<FinancialRecord>) {
        records.forEach { validateRecord(it) }
        dao.replaceAllRecords(records)
    }

    fun getTransactionsByMonth(year: Int, month: Int): Flow<List<FinancialRecord>> = dao.getTransactionsByMonth(year, month)

    fun getTransactionsByDate(date: LocalDate): Flow<List<FinancialRecord>> = dao.getTransactionsByDate(date)
}
