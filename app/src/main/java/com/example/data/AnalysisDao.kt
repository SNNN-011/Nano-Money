package com.example.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryAggregation(
    val category: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val averageAmount: Double
)

@Dao
interface AnalysisDao {
    @Query("""
        SELECT category, 
               SUM(amount) as totalAmount, 
               COUNT(id) as transactionCount, 
               AVG(amount) as averageAmount 
        FROM financial_records 
        WHERE type = 'expense' AND date >= :startMs AND date <= :endMs AND isDeleted = 0
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getExpenseAggregation(startMs: Long, endMs: Long): Flow<List<CategoryAggregation>>

    @Query("""
        SELECT category, 
               SUM(amount) as totalAmount, 
               COUNT(id) as transactionCount, 
               AVG(amount) as averageAmount 
        FROM financial_records 
        WHERE type = 'expense' AND isDeleted = 0
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getAllTimeExpenseAggregation(): Flow<List<CategoryAggregation>>

    @Query("SELECT SUM(amount) FROM financial_records WHERE type = 'expense' AND date >= :startMs AND date <= :endMs AND isDeleted = 0")
    fun getTotalExpense(startMs: Long, endMs: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM financial_records WHERE type = 'expense' AND isDeleted = 0")
    fun getAllTimeTotalExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM financial_records WHERE type = 'income' AND date >= :startMs AND date <= :endMs AND isDeleted = 0")
    fun getTotalIncome(startMs: Long, endMs: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM financial_records WHERE type = 'income' AND isDeleted = 0")
    fun getAllTimeTotalIncome(): Flow<Double?>

    @Query("SELECT * FROM financial_records WHERE type = 'expense' AND date >= :startMs AND date <= :endMs AND isDeleted = 0 ORDER BY date ASC")
    fun getExpenseRecords(startMs: Long, endMs: Long): Flow<List<com.example.data.FinancialRecord>>

    @Query("SELECT * FROM financial_records WHERE type = 'expense' AND isDeleted = 0 ORDER BY date ASC")
    fun getAllTimeExpenseRecords(): Flow<List<com.example.data.FinancialRecord>>

    @Query("SELECT MIN(date) FROM financial_records WHERE type = 'expense' AND isDeleted = 0")
    fun getOldestExpenseDate(): Flow<Long?>
}
