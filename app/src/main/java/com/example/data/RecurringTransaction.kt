package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "recurring_transactions",
    indices = [Index(value = ["syncKey"], unique = true)]
)
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Long,
    val type: String,
    val category: String,
    val dayOfMonth: Int, // 1 to 31
    val notes: String = "",

    /** Soft delete: recurring yang "dihapus" user hanya ditandai agar aksinya ikut tersinkron. */
    @ColumnInfo(defaultValue = "0") override val isDeleted: Boolean = false,

    val lastRunDate: Long? = null, // epoch millis last run or null
    val isActive: Boolean = true,

    /** Waktu edit lokal terakhir (epoch ms). */
    @ColumnInfo(defaultValue = "0") override val updatedAt: Long = 0,

    /** Kunci stabil untuk sinkronisasi. */
    @ColumnInfo(defaultValue = "''") override val syncKey: String = "",

    @ColumnInfo(defaultValue = "0") override val syncStatus: SyncStatus = SyncStatus.PENDING
) : Serializable, SyncEntity