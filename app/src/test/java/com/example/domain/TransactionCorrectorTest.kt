package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Jenis transaksi dari AI harus diperiksa ulang sebelum disimpan.
 *
 * Model sering menandai uang MASUK sebagai pengeluaran ("terima gaji 5 juta"),
 * karena contoh pengeluaran di data latih jauh lebih banyak daripada pemasukan.
 * Prompt saja tidak menutup celah ini, jadi jawabannya diperiksa terhadap dua
 * hal yang tidak bisa dikarang model: daftar kategori milik user dan kalimat
 * asli yang diketik user.
 *
 * Konsekuensi yang disengaja: kalau tidak ada bukti yang cukup, jawaban AI
 * dibiarkan apa adanya. Salah menebak di sini berarti memindahkan transaksi
 * pemasukan jadi pengeluaran (atau sebaliknya) di data keuangan user, jadi
 * ragu-ragu lebih baik daripada menebak.
 */
class TransactionCorrectorTest {

    private val kategoriIncome = listOf("Gaji", "Bonus", "Freelance", "Lainnya")
    private val kategoriExpense = listOf("Makanan", "Transportasi", "Tagihan", "Lainnya")

    private fun jenisAkhir(
        teks: String?,
        jenisAi: String?,
        kategoriAi: String?,
        income: List<String> = kategoriIncome,
        expense: List<String> = kategoriExpense
    ): String = TransactionCorrector.correctType(
        userText = teks,
        aiType = jenisAi,
        aiCategory = kategoriAi,
        incomeCategories = income,
        expenseCategories = expense
    )

    @Test
    fun `jawaban_yang_sudah_benar_tidak_diubah`() {
        assertEquals("expense", jenisAkhir("beli bakso 15 ribu", "expense", "Makanan"))
        assertEquals("income", jenisAkhir("terima gaji 5 juta", "income", "Gaji"))
    }

    @Test
    fun `kategori_pemasukan_menang_atas_jenis_pengeluaran_dari_ai`() {
        // AI menandai pengeluaran, tapi kategorinya sendiri jelas kategori pemasukan.
        assertEquals("income", jenisAkhir("gaji bulan ini 5 juta", "expense", "Gaji"))
        assertEquals("income", jenisAkhir("bonus akhir tahun", "PENGELUARAN", "Bonus"))
    }

    @Test
    fun `kategori_pengeluaran_menang_atas_jenis_pemasukan_dari_ai`() {
        assertEquals("expense", jenisAkhir("isi bensin 50 ribu", "income", "Transportasi"))
        assertEquals("expense", jenisAkhir("bayar makan siang", "PEMASUKAN", "Makanan"))
    }

    @Test
    fun `kategori_penampung_di_dua_daftar_tidak_menentukan_jenis`() {
        // "Lainnya" ada di daftar pemasukan DAN pengeluaran: bukan bukti apa-apa.
        assertEquals("expense", jenisAkhir("bla 5000", "expense", "Lainnya"))
        assertEquals("income", jenisAkhir("bla 5000", "income", "Lainnya"))
    }

    @Test
    fun `terima_gaji_jadi_pemasukan_walau_kategori_ai_penampung`() {
        // Inilah keluhan aslinya: AI membalas pengeluaran + "Lainnya", padahal
        // kalimatnya jelas pemasukan dan user punya kategori "Gaji".
        assertEquals("income", jenisAkhir("terima gaji 5 juta", "expense", "Lainnya"))
    }

    @Test
    fun `bayar_gaji_karyawan_tetap_pengeluaran`() {
        // Kata "gaji" ada, tapi arah uangnya keluar. Kata kerja "bayar" menahan
        // koreksi; tanpa ini, pengeluaran terbesar user bisa berubah jadi pemasukan.
        assertEquals("expense", jenisAkhir("bayar gaji karyawan 5 juta", "expense", "Lainnya"))
    }

    @Test
    fun `kata_kerja_pemasukan_tanpa_nama_kategori_tidak_mengubah_jenis`() {
        // "terima uang 100 ribu" memang pemasukan, tapi tidak ada kategori milik
        // user yang disebut. Menebak dari kata kerja saja pernah salah di kasus
        // lain ("dapat tagihan listrik"), jadi di sini jawaban AI dibiarkan.
        assertEquals("expense", jenisAkhir("terima uang 100 ribu", "expense", "Lainnya"))
    }

    @Test
    fun `dapat_tagihan_listrik_tetap_pengeluaran`() {
        // "dapat" bisa berarti menerima, tapi "dapat tagihan" artinya ditagih.
        assertEquals("expense", jenisAkhir("dapat tagihan listrik 200 ribu", "expense", "Lainnya"))
    }

    @Test
    fun `dapat_bonus_jadi_pemasukan`() {
        assertEquals("income", jenisAkhir("dapat bonus 2 juta", "expense", "Lainnya"))
    }

    @Test
    fun `kata_kerja_dua_arah_tidak_mengubah_jenis`() {
        // Ada sinyal masuk dan keluar sekaligus: buktinya tidak searah, jadi jangan diapa-apakan.
        assertEquals(
            "expense",
            jenisAkhir("bayar makan dulu, lalu terima bonus", "expense", "Lainnya")
        )
    }

    @Test
    fun `kategori_user_yang_punya_emoji_tetap_dikenali`() {
        val income = listOf("\uD83D\uDCB0 Gaji", "Bonus")
        assertEquals("income", jenisAkhir("gajian bulanan 5 juta", "expense", "Gaji", income = income))
    }

    @Test
    fun `daftar_kategori_kosong_tidak_mengubah_jenis`() {
        assertEquals(
            "expense",
            jenisAkhir(
                "terima gaji 5 juta",
                "expense",
                "Gaji",
                income = emptyList(),
                expense = emptyList()
            )
        )
    }

    @Test
    fun `teks_kosong_tidak_mengubah_jenis`() {
        assertEquals("expense", jenisAkhir(null, "expense", "Makanan"))
        assertEquals("expense", jenisAkhir("   ", "expense", "Makanan"))
    }

    @Test
    fun `jenis_yang_tidak_dikenal_dianggap_pengeluaran`() {
        // Perilaku lama dipertahankan: nilai aneh dari AI tidak boleh diam-diam
        // membuat transaksi tercatat sebagai pemasukan.
        assertEquals("expense", jenisAkhir("bla 5000", null, null))
        assertEquals("expense", jenisAkhir("bla 5000", "aneh", "Entah"))
    }

    @Test
    fun `pemetaan_jenis_bawaan_ai_dinormalkan_dengan_aturan_yang_sama`() {
        // Dipakai jalur simpan DAN kartu konfirmasi. Satu tempat saja, supaya kartu
        // tidak pernah menampilkan jenis yang berbeda dari yang akhirnya disimpan.
        assertEquals("income", TransactionCorrector.normalizeType("PEMASUKAN"))
        assertEquals("income", TransactionCorrector.normalizeType(" pemasukan "))
        assertEquals("income", TransactionCorrector.normalizeType("Income"))
        assertEquals("expense", TransactionCorrector.normalizeType("PENGELUARAN"))
        assertEquals("expense", TransactionCorrector.normalizeType("expense"))
        assertEquals("expense", TransactionCorrector.normalizeType(null))
        assertEquals("expense", TransactionCorrector.normalizeType(""))
        assertEquals("expense", TransactionCorrector.normalizeType("aneh"))
    }
}
