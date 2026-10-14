package com.example.data

import kotlinx.coroutines.flow.Flow

class AnalysisRepository(private val dao: AnalysisDao) {

    fun getExpenseAggregation(startMs: Long, endMs: Long): Flow<List<CategoryAggregation>> {
        return dao.getExpenseAggregation(startMs, endMs)
    }

    fun getAllTimeExpenseAggregation(): Flow<List<CategoryAggregation>> {
        return dao.getAllTimeExpenseAggregation()
    }

    fun getTotalExpense(startMs: Long, endMs: Long): Flow<Long?> {
        return dao.getTotalExpense(startMs, endMs)
    }

    fun getAllTimeTotalExpense(): Flow<Long?> {
        return dao.getAllTimeTotalExpense()
    }

    fun getTotalIncome(startMs: Long, endMs: Long): Flow<Long?> {
        return dao.getTotalIncome(startMs, endMs)
    }

    fun getAllTimeTotalIncome(): Flow<Long?> {
        return dao.getAllTimeTotalIncome()
    }

    fun getExpenseRecords(startMs: Long, endMs: Long): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getExpenseRecords(startMs, endMs)
    }

    fun getAllTimeExpenseRecords(): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getAllTimeExpenseRecords()
    }

    /** Income + expense sekaligus, untuk dua garis yang saling menindih. */
    fun getRecordsInRange(startMs: Long, endMs: Long): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getRecordsInRange(startMs, endMs)
    }

    fun getAllRecords(): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getAllRecords()
    }

    fun getOldestExpenseDate(): Flow<Long?> = dao.getOldestExpenseDate()
}
