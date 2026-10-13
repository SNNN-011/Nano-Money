package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kategori milik user. ID stabil: rename hanya mengganti [name] dan mengangkat
 * updatedAt, sehingga budget dan transaksi yang menunjuk kategori ini tetap nyambung.
 */
@Entity(tableName = "categories", indices = [Index("type")])
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "income" or "expense"
    @ColumnInfo(defaultValue = "0") override val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") override val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") override val syncKey: String = "",
    @ColumnInfo(defaultValue = "0") override val syncStatus: SyncStatus = SyncStatus.PENDING
) : SyncEntity