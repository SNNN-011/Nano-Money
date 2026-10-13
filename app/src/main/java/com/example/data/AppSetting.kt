package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Setting kecil yang ikut tersinkron (hanya key di allow-list SyncableSettings).
 * PIN/biometrik/flag keamanan tetap tinggal di encrypted prefs dan tak pernah lewat sync.
 */
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val valueText: String? = null,
    val valueLong: Long? = null,
    val valueBool: Boolean? = null,
    @ColumnInfo(defaultValue = "0") override val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") override val syncKey: String = "",
    @ColumnInfo(defaultValue = "0") override val syncStatus: SyncStatus = SyncStatus.PENDING
) : SyncEntity {

    /** Setting selalu hidup; tidak ada soft delete di level setting. */
    override val isDeleted: Boolean get() = false
}