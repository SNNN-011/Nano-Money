package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Penunjuk kategori hanya boleh mengembalikan nama kategori yang benar-benar
 * dimiliki user; kalau tidak ada, jawaban model dibiarkan.
 *
 * Kata yang diuji di sini berasal dari audit 189 salah kategori
 * (`draf_kontras_v1/AUDIT_189.md` di repo dat), dan hasilnya diukur pada 554
 * baris `gold_v6/eval_599.jsonl` oleh `vm/measure_app_layer.py`:
 * kategori 67,9% -> 87,0%.
 */
class CategoryHintsTest {

    private val kategoriBawaan = listOf(
        "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya"
    )

    @Test
    fun `sarapan_dan_makan_menunjuk_makanan`() {
        assertEquals("Makanan", CategoryHints.suggest("sarapan nasi uduk", kategoriBawaan))
        assertEquals("Makanan", CategoryHints.suggest("makan siang 25rb", kategoriBawaan))
    }

    @Test
    fun `cicilan_dan_angsuran_menunjuk_tagihan`() {
        assertEquals("Tagihan", CategoryHints.suggest("cicilan motor 800rb", kategoriBawaan))
        assertEquals("Tagihan", CategoryHints.suggest("angsuran kredit 500rb", kategoriBawaan))
        assertEquals("Tagihan", CategoryHints.suggest("bayar tagihan air 90rb", kategoriBawaan))
    }

    @Test
    fun `topup_tidak_lagi_menunjuk_hiburan`() {
        // "topup" ambigu: topup pulsa itu Tagihan, topup game sudah tertangkap "game".
        assertEquals("Tagihan", CategoryHints.suggest("topup pulsa 50rb", kategoriBawaan))
        assertEquals("Hiburan", CategoryHints.suggest("topup game 100rb", kategoriBawaan))
    }

    @Test
    fun `kata_tanpa_kategori_user_dibiarkan_ke_model`() {
        val tanpaTagihan = listOf("Makanan", "Hiburan", "Lainnya")
        assertNull(CategoryHints.suggest("cicilan motor 800rb", tanpaTagihan))
    }

    @Test
    fun `kesehatan_tanpa_kategori_kesehatan_jadi_belanja`() {
        assertEquals("Belanja", CategoryHints.suggest("beli obat flu", kategoriBawaan))
    }
}
