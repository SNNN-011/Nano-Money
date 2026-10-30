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
 * kategori 67,9% -> 90,4%.
 */
class CategoryHintsTest {

    private val kategoriBawaan = listOf(
        "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya"
    )

    private val kategoriLengkap = listOf(
        "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Kesehatan",
        "Gaji", "Investasi", "Freelance", "Lainnya"
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

    @Test
    fun `kata_sehari_hari_menunjuk_kategori_bawaan`() {
        assertEquals("Makanan", CategoryHints.suggest("jajan di kantin 15rb", kategoriLengkap))
        assertEquals("Makanan", CategoryHints.suggest("gofood 45rb", kategoriLengkap))
        assertEquals("Transportasi", CategoryHints.suggest("ongkos ojek 10rb", kategoriLengkap))
        assertEquals("Transportasi", CategoryHints.suggest("kirim paket j&t 20rb", kategoriLengkap))
        assertEquals("Tagihan", CategoryHints.suggest("bayar kosan 800rb", kategoriLengkap))
        assertEquals("Tagihan", CategoryHints.suggest("top up gopay 100rb", kategoriLengkap))
        assertEquals("Hiburan", CategoryHints.suggest("rekreasi ke ancol 150rb", kategoriLengkap))
        assertEquals("Belanja", CategoryHints.suggest("beli beras dan gula", kategoriLengkap))
    }

    @Test
    fun `kata_kesehatan_tidak_tertukar_belanja`() {
        assertEquals("Kesehatan", CategoryHints.suggest("beli vitamin 90rb", kategoriLengkap))
        assertEquals("Kesehatan", CategoryHints.suggest("cek darah di lab 200rb", kategoriLengkap))
        assertEquals("Kesehatan", CategoryHints.suggest("periksa gigi 300rb", kategoriLengkap))
    }

    @Test
    fun `kata_panjang_menang_atas_kata_pendek`() {
        // "pasta gigi" dan "susu bayi" harus tetap Belanja, bukan Kesehatan/Makanan.
        assertEquals("Belanja", CategoryHints.suggest("beli pasta gigi 20rb", kategoriLengkap))
        assertEquals("Belanja", CategoryHints.suggest("beli susu bayi 120rb", kategoriLengkap))
        assertEquals("Makanan", CategoryHints.suggest("beli susu 25rb", kategoriLengkap))
    }

    @Test
    fun `kata_pemasukan_menunjuk_kategori_pemasukan`() {
        assertEquals("Freelance", CategoryHints.suggest("jualan kue 500rb", kategoriLengkap))
        assertEquals("Lainnya", CategoryHints.suggest("dapat hibah 10 juta", kategoriLengkap))
        // Menjual barang pribadi bukan penghasilan freelance; biar model/fallback
        // yang menentukan ("Lainnya"), bukan kata "jual".
        assertNull(CategoryHints.suggest("jual hp bekas 1,2 juta", kategoriLengkap))
        assertEquals("Gaji", CategoryHints.suggest("slip gaji bulan ini", kategoriLengkap))
        assertEquals("Investasi", CategoryHints.suggest("beli emas 1 gram", kategoriLengkap))
    }
}
