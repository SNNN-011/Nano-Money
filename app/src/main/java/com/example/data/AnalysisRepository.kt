package com.example.data

import kotlinx.coroutines.flow.Flow

class AnalysisRepository(private val dao: AnalysisDao) {

    fun getExpenseAggregation(startMs: Long, endMs: Long): Flow<List<CategoryAggregation>> {
        return dao.getExpenseAggregation(startMs, endMs)
    }

    fun getAllTimeExpenseAggregation(): Flow<List<CategoryAggregation>> {
        return dao.getAllTimeExpenseAggregation()
    }

    fun getTotalExpense(startMs: Long, endMs: Long): Flow<Double?> {
        return dao.getTotalExpense(startMs, endMs)
    }

    fun getAllTimeTotalExpense(): Flow<Double?> {
        return dao.getAllTimeTotalExpense()
    }

    fun getTotalIncome(startMs: Long, endMs: Long): Flow<Double?> {
        return dao.getTotalIncome(startMs, endMs)
    }

    fun getAllTimeTotalIncome(): Flow<Double?> {
        return dao.getAllTimeTotalIncome()
    }

    fun getExpenseRecords(startMs: Long, endMs: Long): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getExpenseRecords(startMs, endMs)
    }

    fun getAllTimeExpenseRecords(): Flow<List<com.example.data.FinancialRecord>> {
        return dao.getAllTimeExpenseRecords()
    }

    fun getOldestExpenseDate(): Flow<Long?> = dao.getOldestExpenseDate()
}
