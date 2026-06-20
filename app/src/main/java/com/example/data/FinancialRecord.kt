package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "financial_records")
data class FinancialRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Double,
    val type: String, // "income" or "expense"
    val category: String,
    val date: Long, // timestamp in ms
    val notes: String,
    val isDeleted: Boolean = false
) : Serializable
