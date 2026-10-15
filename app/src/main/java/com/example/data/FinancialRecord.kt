package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "financial_records",
    indices = [
        Index(value = ["syncKey"], unique = true),
        // M1: kolom panas. Daftar utama & agregat bulanan memfilter isDeleted + date,
        // pemindahan kategori memfilter categoryId, dan antrean sinkron syncStatus = 1.
        Index(value = ["isDeleted", "date"]),
        Index(value = ["categoryId"]),
        Index(value = ["syncStatus"])
    ]
)
data class FinancialRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val amount: Long,
    val type: String, // "income" or "expense"
    val category: String,
    val date: Long, // timestamp in ms
    val notes: String,
    override val isDeleted: Boolean = false,

    /** Kategori yang dirujuk transaksi (categories.id); kosong untuk data lama. */
    @ColumnInfo(defaultValue = "''") val categoryId: String = "",

    /** Waktu edit lokal terakhir (epoch ms). Diisi titik tulis lokal, bukan saat pull remote. */
    @ColumnInfo(defaultValue = "0") override val updatedAt: Long = 0,

    /** Kunci stabil untuk sinkronisasi; sekaligus id dokumen di Firestore. */
    @ColumnInfo(defaultValue = "''") override val syncKey: String = "",

    @ColumnInfo(defaultValue = "0") override val syncStatus: SyncStatus = SyncStatus.PENDING
) : Serializable, SyncEntity {

    /**
     * Nama kategori saat transaksi dibuat. Dipakai sebagai fallback tampilan
     * ketika categoryId kosong (data sebelum migrasi kategori).
     */
    val categoryName: String get() = category
}