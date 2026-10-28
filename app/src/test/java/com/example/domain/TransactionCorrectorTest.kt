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
    fun `kata_kerja_pemasukan_tanpa_nama_kategori_tetap_pemasukan`() {
        // "terima uang 100 ribu" jelas pemasukan. Dulu kalimat seperti ini
        // dibiarkan mengikuti AI karena satu kata kerja dianggap bukti lemah;
        // sekarang kata kerja dipercaya selama tidak dibantah nama kategori arah
        // lawan -- penjaga kasus "dapat tagihan listrik" ada di
        // `dapat_tagihan_listrik_tetap_pengeluaran`.
        assertEquals("income", jenisAkhir("terima uang 100 ribu", "expense", "Lainnya"))
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
    fun `bunga_tabungan_jadi_pemasukan`() {
        // Ditemukan di uji 30 kalimat: "bunga tabungan 45 ribu" dicatat
        // pengeluaran karena "bunga" bukan kata kerja dan tidak ada di daftar
        // frasa pemasukan.
        assertEquals("income", jenisAkhir("bunga tabungan 45 ribu", "expense", "Lainnya"))
        assertEquals("income", jenisAkhir("bunga deposito 120 ribu", "expense", "Lainnya"))
    }

    @Test
    fun `bayar_bunga_tabungan_tetap_pengeluaran`() {
        // Frasa "bunga tabungan" tidak boleh menang kalau user jelas membayar.
        assertEquals(
            "expense",
            jenisAkhir("bayar bunga tabungan 100 ribu", "expense", "Lainnya")
        )
    }

    @Test
    fun `jajan_dan_servis_dihitung_kata_kerja_pengeluaran`() {
        // Ditemukan di uji 30 kalimat: "jajan" dan "servis" tidak dikenali
        // sebagai kata kerja, sehingga kalimatnya ditolak atau arahnya ditanya.
        assertEquals("expense", TransactionCorrector.directionFromVerbs("jajan bakso 15rb"))
        assertEquals("expense", TransactionCorrector.directionFromVerbs("servis motor 150rb"))
        assertEquals("expense", TransactionCorrector.directionFromVerbs("cuci motor 12rb"))
        assertEquals("expense", TransactionCorrector.directionFromVerbs("traktir teman 85rb"))
        assertEquals(true, TransactionCorrector.looksLikeTransaction("servis ac kamar"))
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
    fun `daftar_kategori_kosong_tetap_memakai_kata_kerja_user`() {
        // Kategori user tidak menentukan apa pun di sini; arahnya dibaca dari
        // kalimat user sendiri ("terima gaji"), jadi hasilnya tetap pemasukan
        // walau kedua daftar kategori kosong.
        assertEquals(
            "income",
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
    fun `frasa_uang_masuk_menang_atas_kata_kerja_tujuan`() {
        // "bayar kuliah" di sini cuma menjelaskan uangnya dipakai buat apa,
        // bukan arah transaksinya. Uangnya tetap MASUK.
        assertEquals(
            "income",
            jenisAkhir("dikasih uang sama ayah 1 juta buat bayar kuliah", "PENGELUARAN", "Makanan")
        )
        assertEquals(
            "income",
            jenisAkhir("dapat uang 100 ribu buat beli bakso", "PENGELUARAN", "Makanan")
        )
        // Kebalikannya tetap pengeluaran: uang masuk disebut sesudah kata kerja keluar.
        assertEquals(
            "expense",
            jenisAkhir("beli baju 300 ribu dapat uang dari kakak", "PENGELUARAN", "Belanja")
        )
    }

    @Test
    fun `kata_kerja_khusus_pengeluaran_dikenali`() {
        // "nraktir" (Jawa), "nyumbang"/"sedekah"/"infak" tidak ada di daftar lama,
        // jadi transaksinya sempat ditanyakan arah atau dicatat sebagai pemasukan.
        assertEquals("expense", jenisAkhir("nraktir teman 50 ribu", "income", "Lainnya"))
        assertEquals("expense", jenisAkhir("nyumbang pembangunan masjid 250 ribu", "income", "Lainnya"))
        assertEquals("expense", jenisAkhir("sedekah 100 ribu", "income", "Lainnya"))
        assertEquals("expense", jenisAkhir("infak jumat 50 ribu", "income", "Lainnya"))
    }

    @Test
    fun `warisan_selalu_pemasukan`() {
        assertEquals("income", jenisAkhir("warisan dari kakek 25 juta", "expense", "Lainnya"))
    }

    @Test
    fun `penanda_pemasukan_tanpa_kata_kerja_dikenali`() {
        // Audit 189 salah kategori menemukan "THR Rp 20 juta" dan "insentif 8 juta"
        // tercatat pengeluaran karena daftar lama hanya punya "gaji"/"honor".
        // Diukur di 554 baris eval: jenis 97,7% -> 99,6%.
        assertEquals("income", jenisAkhir("THR Rp 20 juta", "expense", "Lainnya"))
        assertEquals("income", jenisAkhir("insentif 8 juta", "expense", "Lainnya"))
        assertEquals("income", jenisAkhir("tunjangan bulan ini 2 juta", "expense", "Lainnya"))
        assertEquals("income", TransactionCorrector.directionFromVerbs("uang masuk 2 juta"))
    }

    @Test
    fun `uang_masuk_dari_sewa_tetap_pemasukan`() {
        // "sewa" ada di daftar kata keluar, jadi kalimat ini dulu dibalik jadi
        // pengeluaran. "masuk" menahannya, dan kategori AI sendiri menguatkan.
        assertEquals(
            "income",
            jenisAkhir(
                "uang masuk 2 juta dari sewa",
                "income",
                "Investasi",
                income = kategoriIncome + "Investasi"
            )
        )
    }

    @Test
    fun `uang_jajan_dihitung_pemasukan`() {
        // "jajan" adalah kata keluar, padahal "uang jajan" adalah uang yang
        // diterima. Satu-satunya baris seperti ini di eval ikut terbalik.
        assertEquals("income", jenisAkhir("uang uang jajan 5 jutaan", "income", "Lainnya"))
        // Kategori pilihan model pun tidak boleh membalik arahnya.
        assertEquals("income", jenisAkhir("uang jajan 200rb", "expense", "Makanan"))
        assertEquals("income", TransactionCorrector.directionFromVerbs("uang saku 200rb"))
    }

    @Test
    fun `cicilan_dan_angsuran_dihitung_pengeluaran`() {
        // "cicil" sudah ada, tapi pencocokan memakai kata utuh sehingga
        // "cicilan" (bentuk yang dipakai orang) tidak pernah cocok.
        assertEquals("expense", TransactionCorrector.directionFromVerbs("cicilan motor 800rb"))
        assertEquals("expense", TransactionCorrector.directionFromVerbs("bayar angsuran 500rb"))
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
