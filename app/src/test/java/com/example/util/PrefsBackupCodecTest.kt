package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsBackupCodecTest {

    @Test
    fun bolak_balik_semua_tipe() {
        val original = linkedMapOf<String, Any>(
            "budget_bulanan" to 1500000L,
            "migrasi_selesai" to true,
            "jumlah_kategori" to 12,
            "rasio_tampilan" to 0.5f,
            "kategori_csv" to "Makan,Transport\nLainnya 100%",
            "kategori_hidden" to setOf("Makan", "Jajan, diskon"),
            "kunci aneh \u0002dengan pemisah" to "nilai aneh \u0002 dengan pemisah",
            "set_kosong" to emptySet<String>()
        )

        val text = PrefsBackupCodec.exportMap(original)
        val restored = PrefsBackupCodec.importMap(text)

        assertEquals(original, restored)
    }

    @Test
    fun kunci_teknis_dan_penanda_migrasi_dilewati() {
        val original = mapOf<String, Any>(
            "__androidx_security_crypto_encrypted_prefs_key_keyset__" to "rahasia",
            "security_migration_completed_v1" to true,
            "punya_pin" to true
        )
        val restored = PrefsBackupCodec.importMap(PrefsBackupCodec.exportMap(original))
        assertEquals(setOf("punya_pin"), restored.keys)
    }

    @Test
    fun baris_rusak_dilewati() {
        val restored = PrefsBackupCodec.importMap(
            "i\u0002angka\u0002bukan-angka\nb\u0002bendera\u00021\nbaris-ngawur\n"
        )
        assertEquals(mapOf("bendera" to true), restored)
    }
}
