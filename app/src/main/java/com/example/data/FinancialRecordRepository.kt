package com.example.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FinancialRecordRepository(private val dao: FinancialRecordDao) {
    val allRecords: Flow<List<FinancialRecord>> = dao.getAllRecords()

    suspend fun getRecordCount(): Int = dao.getRecordCount()

    suspend fun insert(record: FinancialRecord): Long = dao.insertRecord(record)

    suspend fun update(record: FinancialRecord) = dao.updateRecord(record)

    suspend fun delete(record: FinancialRecord) = dao.deleteRecord(record)

    suspend fun replaceAll(records: List<FinancialRecord>) = dao.replaceAllRecords(records)

    fun getTransactionsByMonth(year: Int, month: Int): Flow<List<FinancialRecord>> = dao.getTransactionsByMonth(year, month)

    fun getTransactionsByDate(date: LocalDate): Flow<List<FinancialRecord>> = dao.getTransactionsByDate(date)
}
