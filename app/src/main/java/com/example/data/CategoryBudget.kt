package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Batas anggaran per kategori. Terikat ke [CategoryBudget.categoryId] (bukan nama),
 * sehingga rename kategori tidak membuat budget yatim.
 */
@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val categoryId: String,
    @ColumnInfo(defaultValue = "0") val budgetLimit: Long = 0L,
    @ColumnInfo(defaultValue = "0") override val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") override val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") override val syncKey: String = "",
    @ColumnInfo(defaultValue = "0") override val syncStatus: SyncStatus = SyncStatus.PENDING
) : SyncEntity