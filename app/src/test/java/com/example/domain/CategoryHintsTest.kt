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

    private val bawaanBaruIncome = listOf(
        "Gaji", "Uang Saku", "Freelance", "Bisnis",
        "Investasi", "Bonus", "Hadiah", "Lainnya"
    )

    private val bawaanBaruExpense = listOf(
        "Makanan & Minuman", "Transportasi", "Belanja", "Kebutuhan Rumah",
        "Tagihan & Utilitas", "Pendidikan", "Kesehatan", "Hiburan",
        "Langganan", "Internet & Pulsa", "Perawatan Diri", "Donasi", "Lainnya"
    )

    /** Daftar sesuai jenis transaksi, seperti yang dipakai parser (bukan campuran). */
    private val lengkapIncome = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
    private val lengkapExpense = listOf(
        "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Kesehatan", "Lainnya"
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
    fun `donasi_tanpa_kategori_donasi_jadi_lainnya`() {
        // Uji 30 kalimat baru: tanpa cadangan ini donasi jatuh ke tebakan model
        // (pernah jadi Kesehatan). Kalau user punya kategori Donasi, nama itu
        // yang dipakai.
        assertEquals(
            "Lainnya",
            CategoryHints.suggest("sedekah ke panti asuhan 150rb", lengkapExpense)
        )
        assertEquals(
            "Donasi",
            CategoryHints.suggest("sedekah ke panti asuhan 150rb", bawaanBaruExpense)
        )
    }

    @Test
    fun `kata_kesehatan_tidak_tertukar_belanja`() {
        assertEquals("Kesehatan", CategoryHints.suggest("beli vitamin 90rb", kategoriLengkap))
        assertEquals("Kesehatan", CategoryHints.suggest("cek darah di lab 200rb", kategoriLengkap))
        assertEquals("Kesehatan", CategoryHints.suggest("periksa gigi 300rb", kategoriLengkap))
    }

    @Test
    fun `kata_panjang_menang_atas_kata_pendek`() {
        // "susu bayi" harus tetap Belanja, bukan Makanan.
        assertEquals("Belanja", CategoryHints.suggest("beli susu bayi 120rb", lengkapExpense))
        assertEquals("Makanan", CategoryHints.suggest("beli susu 25rb", lengkapExpense))
        // "pasta gigi" bukan "gigi" (Kesehatan) dan bukan Belanja kalau
        // kategori Perawatan Diri ada.
        assertEquals("Perawatan Diri", CategoryHints.suggest("beli pasta gigi 20rb", bawaanBaruExpense))
        assertEquals("Belanja", CategoryHints.suggest("beli susu bayi 120rb", bawaanBaruExpense))
    }

    @Test
    fun `kata_pemasukan_menunjuk_kategori_pemasukan`() {
        assertEquals("Freelance", CategoryHints.suggest("jualan kue 500rb", lengkapIncome))
        assertEquals("Lainnya", CategoryHints.suggest("dapat hibah 10 juta", lengkapIncome))
        // Menjual barang pribadi bukan penghasilan freelance; biar model/fallback
        // yang menentukan ("Lainnya"), bukan kata "jual".
        assertNull(CategoryHints.suggest("jual hp bekas 1,2 juta", lengkapIncome))
        assertEquals("Gaji", CategoryHints.suggest("slip gaji bulan ini", lengkapIncome))
        assertEquals("Investasi", CategoryHints.suggest("beli emas 1 gram", lengkapIncome))
    }

    @Test
    fun `semua_kategori_bawaan_baru_dikenali_dari_kata_kuncinya`() {
        val expense = bawaanBaruExpense
        val income = bawaanBaruIncome

        assertEquals("Gaji", CategoryHints.suggest("gaji bulanan 5 juta", income))
        assertEquals("Uang Saku", CategoryHints.suggest("uang saku 200rb", income))
        assertEquals("Freelance", CategoryHints.suggest("fee proyek desain 1 juta", income))
        assertEquals("Bisnis", CategoryHints.suggest("penjualan online 2 juta", income))
        assertEquals("Investasi", CategoryHints.suggest("dividen 500rb", income))
        assertEquals("Bonus", CategoryHints.suggest("bonus tahunan 2 juta", income))
        assertEquals("Hadiah", CategoryHints.suggest("dapat hadiah 300rb", income))
        assertEquals("Lainnya", CategoryHints.suggest("dapat kiriman 100rb", income))

        assertEquals("Makanan & Minuman", CategoryHints.suggest("makan siang 25rb", expense))
        assertEquals("Transportasi", CategoryHints.suggest("isi bensin 50rb", expense))
        assertEquals("Belanja", CategoryHints.suggest("beli sepatu 300rb", expense))
        assertEquals("Kebutuhan Rumah", CategoryHints.suggest("beli sabun 15rb", expense))
        assertEquals("Tagihan & Utilitas", CategoryHints.suggest("bayar listrik 200rb", expense))
        assertEquals("Pendidikan", CategoryHints.suggest("bayar spp 500rb", expense))
        assertEquals("Kesehatan", CategoryHints.suggest("beli obat 30rb", expense))
        assertEquals("Hiburan", CategoryHints.suggest("nonton bioskop 50rb", expense))
        assertEquals("Langganan", CategoryHints.suggest("netflix 54rb", expense))
        assertEquals("Internet & Pulsa", CategoryHints.suggest("beli pulsa 25rb", expense))
        assertEquals("Perawatan Diri", CategoryHints.suggest("beli skincare 150rb", expense))
        assertEquals("Donasi", CategoryHints.suggest("sedekah 100rb", expense))
    }

    @Test
    fun `kategori_lama_tetap_dilayani_lewat_cadangan_nama_baru`() {
        // User yang belum migrasi v9 masih punya nama lama. Kata baru harus
        // jatuh ke kategori lama yang setara, bukan dilewati.
        assertEquals("Belanja", CategoryHints.suggest("beli sabun 15rb", kategoriLengkap))
        assertEquals("Tagihan", CategoryHints.suggest("bayar spp 500rb", kategoriLengkap))
        assertEquals("Hiburan", CategoryHints.suggest("netflix 54rb", kategoriLengkap))
        assertEquals("Tagihan", CategoryHints.suggest("beli pulsa 25rb", kategoriLengkap))
        assertEquals("Tagihan", CategoryHints.suggest("wifi bulanan 300rb", kategoriLengkap))

        val incomeLama = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
        assertEquals("Lainnya", CategoryHints.suggest("uang saku 200rb", incomeLama))
        assertEquals("Freelance", CategoryHints.suggest("penjualan online 2 juta", incomeLama))
        assertEquals("Gaji", CategoryHints.suggest("bonus tahunan 2 juta", incomeLama))
        assertEquals("Lainnya", CategoryHints.suggest("dapat hadiah 300rb", incomeLama))
    }

    @Test
    fun `wifi_pindah_ke_internet_pulsa_hanya_kalau_kategorinya_ada`() {
        assertEquals("Internet & Pulsa", CategoryHints.suggest("wifi bulanan 300rb", bawaanBaruExpense))
        assertEquals("Tagihan", CategoryHints.suggest("wifi bulanan 300rb", kategoriLengkap))
    }

    @Test
    fun `penampung_lainnya_dipakai_kalau_tidak_ada_kata_yang_cocok`() {
        assertEquals(
            "Lainnya",
            CategoryResolver.finalCategory("entah apa", bawaanBaruExpense)
        )
        assertEquals(
            "Lainnya",
            CategoryResolver.finalCategory(null, bawaanBaruExpense)
        )
    }
}
