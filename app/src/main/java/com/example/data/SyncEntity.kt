package com.example.data

/**
 * Status sinkronisasi satu baris lokal terhadap Firestore.
 *
 * SYNCED  : sudah sama dengan versi di cloud.
 * PENDING : ada perubahan lokal yang belum terkirim.
 */
enum class SyncStatus {
    SYNCED,
    PENDING
}

/**
 * Kontrak minimum agar sebuah entitas Room bisa disinkronkan dengan versi cloud-nya.
 * Semua entitas yang ikut jalur sinkronisasi wajib mengimplementasikan interface ini.
 */
interface SyncEntity {
    val syncKey: String
    val updatedAt: Long
    val isDeleted: Boolean
    val syncStatus: SyncStatus
}