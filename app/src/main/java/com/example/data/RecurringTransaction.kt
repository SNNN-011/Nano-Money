package com.example.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "recurring_transactions")
@Parcelize
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Long,
    val type: String, // "income" or "expense"
    val category: String,
    val dayOfMonth: Int, // 1 to 31
    val notes: String = "",
    val lastRunDate: Long? = null, // epoch millis last run or null
    val isActive: Boolean = true
) : Parcelable
