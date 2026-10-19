package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pembaca nominal dari kalimat user.
 *
 * Angka harapan di bawah bukan karangan: semuanya kasus yang sudah diukur
 * salah saat model diuji lewat jalur aplikasi (lihat app_path_probe.py),
 * jadi test ini mengunci perbaikan itu supaya tidak balik lagi.
 */
class AmountReaderTest {

    @Test
    fun ribu_dan_juta_dibaca_benar() {
        assertEquals(25_000L, AmountReader.fromText("beli bakso 25 ribu"))
        assertEquals(350_000L, AmountReader.fromText("bayar listrik 350 ribu"))
        assertEquals(2_000_000L, AmountReader.fromText("sewa kos 2 juta"))
        assertEquals(5_000_000L, AmountReader.fromText("terima gaji 5 juta"))
        assertEquals(800_000L, AmountReader.fromText("kemarin beli sepatu 800 ribu"))
        assertEquals(200_000L, AmountReader.fromText("uang 200 ribu"))
    }

    @Test
    fun singkatan_yang_biasa_dipakai_ikut_dibaca() {
        assertEquals(100_000L, AmountReader.fromText("isi bensin 100rb"))
        assertEquals(18_000L, AmountReader.fromText("jajan kopi 18rb"))
        assertEquals(50_000L, AmountReader.fromText("topup gopay 50rb"))
        assertEquals(3_000_000L, AmountReader.fromText("dibayar klien 3jt"))
    }

    @Test
    fun `varian_penulisan_dari_data_latih_ikut_dibaca`() {
        // Semua varian ini benar-benar dipakai di data latih/ujian.
        assertEquals(45_000L, AmountReader.fromText("jajan di SPBU Total Rp 45 rebu"))
        assertEquals(45_000L, AmountReader.fromText("habis Rp 45 rebu di Blibli"))
        assertEquals(40_000L, AmountReader.fromText("catet Rp 40 reibu di Air Galon"))
        assertEquals(20_000L, AmountReader.fromText("catetin Rp 20 rbuan di Lazada"))
        assertEquals(2_500_000L, AmountReader.fromText("terima kasbon Rp 2,5 jta"))
        assertEquals(2_500_000L, AmountReader.fromText("omzet cair Rp 2,5 jta"))
        assertEquals(1_500_000L, AmountReader.fromText("dapat 1,5 jutaan"))
    }

    @Test
    fun angka_desimal_dibaca() {
        assertEquals(1_500_000L, AmountReader.fromText("belanja bulanan 1,5 juta"))
        assertEquals(1_200_000L, AmountReader.fromText("jual hp bekas 1,2 juta"))
        assertEquals(2_500_000L, AmountReader.fromText("2.5 juta buat sewa"))
    }

    @Test
    fun titik_tiga_digit_dianggap_pemisah_ribuan() {
        // "1.500rb" = 1500 ribu, bukan 1,5 ribu.
        assertEquals(1_500_000L, AmountReader.fromText("dapat 1.500rb"))
    }

    @Test
    fun angka_bertanda_rupiah_dibaca() {
        assertEquals(15_000L, AmountReader.fromText("nasi goreng Rp 15000"))
        assertEquals(2_500_000L, AmountReader.fromText("bayar kos Rp2.500.000"))
        assertEquals(1_500L, AmountReader.fromText("parkir rp 1.500"))
    }

    @Test
    fun satuan_menang_atas_tanda_rupiah() {
        // "Rp 2,5 juta" harus 2,5 juta, bukan 2 rupiah.
        assertEquals(2_500_000L, AmountReader.fromText("tanggal 28 catat pengeluaran Rp 2,5 juta"))
    }

    @Test
    fun yang_disebut_pertama_dipakai() {
        val teks = "bayar tagihan wifi 300rb sama beli pulsa 25 ribu"
        assertEquals(300_000L, AmountReader.fromText(teks))
    }

    @Test
    fun angka_bertitik_ribuan_dibaca() {
        // Model salah ×10 pada angka seperti ini (2.500.000 dibalas 250000).
        assertEquals(2_500_000L, AmountReader.fromText("uang keluar 2.500.000"))
        assertEquals(5_000L, AmountReader.fromText("catat pengeluaran 5.000 tadi"))
        assertEquals(10_000_000L, AmountReader.fromText("beli 10.000.000 Sate Taichan"))
        assertEquals(3_500_000L, AmountReader.fromText("bayar Erafone 3.500.000"))
    }

    @Test
    fun tanggal_bertitik_tidak_dibaca_sebagai_nominal() {
        // "19.04.2026" bukan nominal; nominal yang benar ada di "50.000".
        assertEquals(50_000L, AmountReader.fromText("tgl 19.04.2026 bayar 50.000"))
        assertNull(AmountReader.fromText("catat tgl 19.04.2026"))
        assertNull(AmountReader.fromText("tgl 25.06.2026 beli sesuatu"))
    }

    @Test
    fun angka_telanjang_dibaca_kalau_jelas_nominal() {
        // Nominal tanpa satuan tetap nominal. Sebelumnya sengaja tidak dibaca
        // karena takut menelan tanggal/jam; sekarang dibaca dengan penjaga.
        assertEquals(15_000L, AmountReader.fromText("nasi goreng 15000"))
        assertEquals(50_000L, AmountReader.fromText("bayar bensin 50000"))
        assertEquals(1_500L, AmountReader.fromText("parkir 1500"))
        assertEquals(2_000_000L, AmountReader.fromText("uang masuk 2000000,-"))
    }

    @Test
    fun angka_kecil_bukan_nominal() {
        // Di bawah 1000 lebih mungkin jumlah barang/hari daripada uang.
        assertNull(AmountReader.fromText("tanggal 28 beli sesuatu"))
        assertNull(AmountReader.fromText("jam 7 bayar parkir"))
        assertNull(AmountReader.fromText("beli 999 permen"))
    }

    @Test
    fun angka_tanggal_jam_tahun_dan_nomor_hp_tidak_dibaca() {
        // Penjaganya bukan karangan: 308 baris uji memuat angka tanggal tanpa
        // nominal ("19-06", "4 hari lalu"), dan angka jam/nomor HP pernah jadi
        // kandidat nominal.
        assertNull(AmountReader.fromText("jam 1230 berangkat kerja"))
        assertNull(AmountReader.fromText("tanggal 1234 catat"))
        assertNull(AmountReader.fromText("tahun 2026 beli sepeda"))
        assertNull(AmountReader.fromText("hp 081234567890"))
        assertNull(AmountReader.fromText("wa 081234567890 bayar utang"))
    }

    @Test
    fun angka_empat_digit_di_rentang_tahun_ditolak() {
        assertNull(AmountReader.fromText("bayar 2026"))
        // 1500 tidak masuk rentang tahun, jadi tetap dibaca.
        assertEquals(1_500L, AmountReader.fromText("bayar 1500"))
    }

    @Test
    fun semua_nominal_dibaca_urut_posisi() {
        val teks = "gajian 5jt, beli es 5k, beli bensin 50rbu"
        assertEquals(
            listOf(5_000_000L, 5_000L, 50_000L),
            AmountReader.fromTextAll(teks)
        )
    }

    @Test
    fun satu_nominal_atau_angka_bukan_nominal_tidak_dianggap_banyak() {
        assertFalse(AmountReader.looksLikeMultiple("beli es 5rb"))
        assertFalse(AmountReader.looksLikeMultiple("uang 200 ribu"))
        // "2 porsi" dan "3 hari" bukan nominal, jadi bukan transaksi kedua.
        assertFalse(AmountReader.looksLikeMultiple("beli 2 bensin 50000"))
        assertFalse(AmountReader.looksLikeMultiple("bayar kos 3 hari 500000"))
        assertFalse(AmountReader.looksLikeMultiple("bayar Rp2.500.000"))
    }

    @Test
    fun beberapa_nominal_dianggap_banyak_transaksi() {
        assertTrue(AmountReader.looksLikeMultiple("gajian 5jt, beli es 5k, beli bensin 50rbu"))
        assertTrue(AmountReader.looksLikeMultiple("bayar wifi 300rb sama beli pulsa 25 ribu"))
        // Nominal yang sama dua kali tetap dua transaksi.
        assertTrue(AmountReader.looksLikeMultiple("parkir 5000, parkir lagi 5000"))
    }

    @Test
    fun kalimat_tanpa_angka_menghasilkan_null() {
        assertNull(AmountReader.fromText("bayar kos"))
        assertNull(AmountReader.fromText("beli obat di apotek"))
        assertNull(AmountReader.fromText("apa kabar bot?"))
    }

    @Test
    fun kata_yang_mengandung_k_tidak_dianggap_satuan() {
        // "3 kucing" bukan 3 ribu.
        assertNull(AmountReader.fromText("beli 3 kucing"))
    }
}
