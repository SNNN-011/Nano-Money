package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Double,
    val type: String, // "income" or "expense"
    val category: String,
    val dayOfMonth: Int, // 1 to 31
    val notes: String = "",
    val lastRunDate: Long? = null, // epoch millis last run or null
    val isActive: Boolean = true
) : Serializable
