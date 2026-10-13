package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga allow-list setting. Ini yang membuat PIN/biometrik mustahil ikut terkirim
 * ke Firestore: daftar putih, bukan daftar hitam.
 */
class SyncableSettingsTest {

    @Test
    fun hanya_anggaran_bulanan_yang_boleh_sync() {
        assertTrue(SyncableSettings.isSyncable(SyncableSettings.MONTHLY_BUDGET_LIMIT))
        assertFalse(SyncableSettings.isSyncable("saved_pin"))
        assertFalse(SyncableSettings.isSyncable("pin_hash"))
        assertFalse(SyncableSettings.isSyncable("biometric_enabled"))
        assertFalse(SyncableSettings.isSyncable("security_question"))
        assertFalse(SyncableSettings.isSyncable("auto_backup_enabled"))
        assertFalse(SyncableSettings.isSyncable("tidak_ada_seperti_ini"))
    }

    @Test
    fun kunci_lokal_tidak_pernah_masuk_allow_list() {
        SyncableSettings.LOCAL_ONLY_KEYS.forEach { key ->
            assertFalse(
                "kunci lokal '$key' tidak boleh syncable",
                SyncableSettings.isSyncable(key)
            )
        }
    }

    @Test
    fun allow_list_dan_lokal_only_tidak_beririsan() {
        val overlap = SyncableSettings.ALLOW_LIST intersect SyncableSettings.LOCAL_ONLY_KEYS
        assertEquals(emptySet<String>(), overlap)
    }

    @Test
    fun allow_listed_membuang_kunci_terlarang_dan_menahankan_urutan() {
        val input = listOf(
            "saved_pin",
            SyncableSettings.MONTHLY_BUDGET_LIMIT,
            "pin_salt",
            "monthly_budget_limit"
        )
        assertEquals(
            listOf(SyncableSettings.MONTHLY_BUDGET_LIMIT, SyncableSettings.MONTHLY_BUDGET_LIMIT),
            SyncableSettings.allowListed(input)
        )
    }
}