package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi bug 2026-10-17: migrasi di SecurePrefsHelper memindai file prefs-nya sendiri
 * lalu mengenkripsi ulang entri terenkripsi ke file yang sama, sehingga file membengkak
 * ~1,34x setiap aplikasi dibuka sampai OutOfMemoryError.
 */
class PrefsGuardTest {

    @Test
    fun fileYangSudahTerenkripsiTidakBolehDimigrasi() {
        // Kasus yang dulu membuat aplikasi crash: file sudah punya keyset sendiri.
        assertFalse(PrefsGuard.canMigrate(hasKeysetMarker = true, migrationDone = false))
    }

    @Test
    fun filePlainPeninggalanVersiLamaTetapDimigrasi() {
        assertTrue(PrefsGuard.canMigrate(hasKeysetMarker = false, migrationDone = false))
    }

    @Test
    fun migrasiYangSudahPernahJalanTidakDiulang() {
        assertFalse(PrefsGuard.canMigrate(hasKeysetMarker = false, migrationDone = true))
    }

    @Test
    fun fileYangMembengkakDianggapRusak() {
        assertFalse(PrefsGuard.isOversized(64 * 1024))
        assertFalse(PrefsGuard.isOversized(PrefsGuard.MAX_PREFS_BYTES))
        // Ukuran nyata file rusak di perangkat pengguna saat bug ini ditemukan.
        assertTrue(PrefsGuard.isOversized(110_657_208L))
    }
}
