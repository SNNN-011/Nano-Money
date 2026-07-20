package com.example.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "financial_records")
@Parcelize
data class FinancialRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Long,
    val type: String, // "income" or "expense"
    val category: String,
    val date: Long, // timestamp in ms
    val notes: String,
    val isDeleted: Boolean = false
) : Parcelable
