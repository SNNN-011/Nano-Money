package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kategori milik user harus menang atas jawaban AI yang tidak persis sama.
 *
 * AI mengirim nama kategori dengan bentuk yang berbeda: huruf besar-kecil lain,
 * ada emoji atau tidak, atau memakai kata yang lebih umum ("Kopi susu" untuk
 * kategori "Kopi"). Kalau jawaban dipakai mentah, kategori user tidak pernah
 * terpakai dan AI diam-diam membuat kategori baru yang isinya dobel.
 *
 * Catatan: kasus AI membalas kategori umum yang tidak mirip sama sekali dengan
 * kategori user ("Hiburan" padahal user cuma punya "Game") TIDAK bisa ditangani
 * di sini. Tidak ada cara menebak bahwa maksudnya "Game" tanpa salah juga di
 * kasus lain. Itu dicegah lewat prompt, bukan lewat pencocokan.
 *
 * Konsekuensi yang disengaja: hasil NeedsNewCategory berarti pemanggil harus
 * memakai kategori penampung, BUKAN membuat kategori baru. Kalau pemanggil
 * membuatkannya, kategori yang sudah dihapus user akan muncul kembali -- AI
 * mengarang "Belanja" dari pengetahuannya sendiri, pengecekan duplikat hanya
 * membandingkan kategori aktif, jadi "Belanja" lolos dan dibuat ulang.
 */
class CategoryResolverTest {

    private val kategoriUser = listOf(
        "Game",
        "Kopi",
        "Ojek",
        "Makanan",
        "Transportasi",
        "Gaji"
    )

    private fun harusCocok(ai: String, tersedia: List<String> = kategoriUser): String {
        val hasil = CategoryResolver.resolve(ai, tersedia)
        assertTrue("harusnya ketemu kategori, bukan: $hasil", hasil is CategoryResolution.Matched)
        return (hasil as CategoryResolution.Matched).category
    }

    private fun harusBaru(ai: String, tersedia: List<String> = kategoriUser): String {
        val hasil = CategoryResolver.resolve(ai, tersedia)
        assertTrue(
            "harusnya minta kategori baru, bukan: $hasil",
            hasil is CategoryResolution.NeedsNewCategory
        )
        return (hasil as CategoryResolution.NeedsNewCategory).suggestedName
    }

    @Test
    fun `kategori_yang_benar_tidak_diubah`() {
        assertEquals("Makanan", harusCocok("Makanan"))
        assertEquals("Ojek", harusCocok("Ojek"))
        assertEquals("Gaji", harusCocok("Gaji"))
    }

    @Test
    fun `huruf_besar_kecil_tidak_penting`() {
        assertEquals("Game", harusCocok("game"))
        assertEquals("Game", harusCocok("  GAME  "))
        assertEquals("Kopi", harusCocok("kopi"))
    }

    @Test
    fun `emoji_pada_jawaban_ai_dibuang_sebelum_dicocokkan`() {
        // Kategori user ada emodinya, jawaban AI tidak. \uD83C\uDFAE = controller game.
        val denganEmoji = "\uD83C\uDFAE Game"
        assertEquals(denganEmoji, harusCocok("Game", listOf(denganEmoji, "Makanan")))
        assertEquals(denganEmoji, harusCocok(denganEmoji, listOf(denganEmoji, "Makanan")))
    }

    @Test
    fun `kategori_user_yang_punya_emoji_masih_cocok_dan_kembaliannya_utuh`() {
        // Yang dikembalikan harus persis seperti tersimpan di Room, supaya
        // pemanggil tidak menulis ulang nama kategorinya dan tidak membuat dobel.
        val denganEmoji = "\uD83C\uDFAE Game"
        assertEquals(denganEmoji, harusCocok("Game", listOf(denganEmoji, "Makanan")))
    }

    @Test
    fun `kata_yang_sama_dipakai_mencocokkan`() {
        assertEquals("Kopi", harusCocok("Kopi susu"))
        assertEquals("Kopi susu", harusCocok("Kopi Susu", listOf("Kopi susu")))
    }

    @Test
    fun `kategori_yang_beririsan_huruf_tidak_ikut_cocok`() {
        // "Hobi" tidak boleh ikut cocok dengan "Hiburan" dan sebaliknya.
        assertEquals("hiburan", harusBaru("Hiburan", listOf("Hobi", "Buku")))
    }

    @Test
    fun `kategori_asing_yang_tidak_mirip_membuat_kategori_baru`() {
        assertEquals("kursus", harusBaru("Kursus"))
    }

    @Test
    fun `jawaban_kosong_tidak_membuat_kategori_kosong`() {
        val hasil = CategoryResolver.resolve(null, kategoriUser)
        assertTrue(hasil is CategoryResolution.NeedsNewCategory)
        assertEquals("", (hasil as CategoryResolution.NeedsNewCategory).suggestedName)

        assertEquals("", harusBaru("   "))
    }

    @Test
    fun `daftar_kosong_selalu_minta_kategori_baru`() {
        assertTrue(CategoryResolver.resolve("Game", emptyList()) is CategoryResolution.NeedsNewCategory)
    }

    @Test
    fun `kategori_yang_sudah_dihapus_tidak_bisa_dikembalikan_ai`() {
        // User menghapus "Belanja". Daftar aktif tinggal ["Perawatan Tubuh", "Lainnya"].
        // AI tetap akan mengarang "Belanja" dari pengetahuannya sendiri, dan itu
        // harus tetap jadi NeedsNewCategory supaya pemanggil memakai penampung.
        // Kalau ini sampai Matched, kategori yang dihapus user hidup lagi.
        val setelahDihapus = listOf("Perawatan Tubuh", "Lainnya")

        val hasil = CategoryResolver.resolve("Belanja", setelahDihapus)
        assertTrue(
            "kategori yang sudah dihapus tidak boleh dicocokkan lagi",
            hasil is CategoryResolution.NeedsNewCategory
        )
    }

    @Test
    fun `kategori_penampung_sendiri_selalu_cocok_kalau_ada`() {
        // Kalau AI menjawab "Lainnya" dan kategori itu memang ada, itu kategori
        // yang dipakai -- bukan perlu membuat yang baru.
        assertEquals("Lainnya", harusCocok("Lainnya", listOf("Makanan", "Lainnya")))
        assertEquals("Lainnya", harusCocok("lainnya", listOf("Makanan", "Lainnya")))
    }

    @Test
    fun `entri_kosong_di_daftar_tidak_pernah_jadi_hasil_pencocokan`() {
        // Nama kategori yang berisi spasi saja akan ternormalisasi jadi kosong.
        // Kalau tidak dilewati, kategori pertama yang kosong itu yang terkirim balik
        // dan transaksi tersimpan tanpa kategori.
        assertEquals("Game", harusCocok("Game", listOf("", "   ", "Game", "Makanan")))
    }

    @Test
    fun `salah_ketik_satu_huruf_pada_kata_panjang_masih_cocok`() {
        // Model yang cuma keliru satu huruf tidak layak membuat kategori baru.
        assertEquals("Makanan", harusCocok("makanana"))
        assertEquals("Transportasi", harusCocok("trasportasi", listOf("Transportasi", "Makanan")))
    }

    @Test
    fun `kata_pendek_beda_satu_huruf_tidak_ikut_cocok`() {
        // "Kopi" dan "Kopu" terlalu pendek untuk ditebak; salah tebak di sini
        // memindahkan transaksi ke kategori lain tanpa user sadari.
        assertEquals("kopu", harusBaru("Kopu", listOf("Kopi", "Makanan")))
    }

    @Test
    fun `kata_panjang_yang_berbeda_jauh_tidak_ikut_cocok`() {
        assertEquals("kursus", harusBaru("Kursus", listOf("Konsumsi", "Makanan")))
    }

    // ---- Kategori akhir: dipakai bersama kartu konfirmasi dan penyimpanan ----

    @Test
    fun `kategori_akhir_memakai_kategori_user_saat_cocok`() {
        assertEquals("Tagihan", CategoryResolver.finalCategory("tagihan", listOf("Makanan", "Tagihan")))
    }

    @Test
    fun `kategori_akhir_masuk_penampung_saat_tidak_cocok`() {
        // "Belanja" tidak ada di daftar user: jangan dikarang, pakai penampung.
        assertEquals("Lainnya", CategoryResolver.finalCategory("Belanja", listOf("Makanan", "Tagihan")))
    }

    @Test
    fun `kategori_akhir_memakai_penampung_milik_user_jika_ada`() {
        // Yang dikembalikan harus persis seperti tersimpan di Room (emoji ikut),
        // supaya kartu dan penyimpanan menulis nama kategori yang sama.
        // ponytail: cuma emoji yang dikenal CategoryIconMapper yang ikut terbaca;
        // emoji di luar daftar itu sudah jadi perilaku lama, bukan dari perubahan ini.
        val penampung = "\uD83D\uDCB0 Lainnya"
        assertEquals(penampung, CategoryResolver.finalCategory("Belanja", listOf("Makanan", penampung)))
    }

    @Test
    fun `kategori_akhir_tetap_penampung_walau_user_menghapusnya`() {
        // Kategori penampung sengaja tidak dibuatkan diam-diam: nama itu sudah jadi
        // penanda di tempat lain, dan membuatnya kembali membatalkan pilihan user.
        assertEquals("Lainnya", CategoryResolver.finalCategory("Belanja", listOf("Makanan")))
    }

    @Test
    fun `kategori_akhir_dari_jawaban_kosong_masuk_penampung`() {
        assertEquals("Lainnya", CategoryResolver.finalCategory(null, listOf("Makanan")))
        assertEquals("Lainnya", CategoryResolver.finalCategory("   ", listOf("Makanan")))
    }

    @Test
    fun `nama_kategori_yang_disebut_user_ditemukan_walau_kata_terpisah`() {
        val kategori = listOf("Kopi", "Servis Motor", "Kucing")
        assertEquals(
            "Servis Motor",
            CategoryResolver.categoryMentionedIn("servis rem motor 150rb", kategori)
        )
        assertEquals(
            "Kucing",
            CategoryResolver.categoryMentionedIn("makanan kucing 50rb", kategori)
        )
    }

    @Test
    fun `kategori_dengan_nama_lebih_panjang_menang`() {
        val kategori = listOf("Kopi", "Kopi Susu")
        assertEquals(
            "Kopi Susu",
            CategoryResolver.categoryMentionedIn("beli kopi susu 22rb", kategori)
        )
    }

    @Test
    fun `kata_yang_mirip_tapi_bukan_nama_kategori_tidak_dianggap_sebutan`() {
        val kategori = listOf("Kopi", "Servis Motor")
        assertEquals(null, CategoryResolver.categoryMentionedIn("ngopi 20rb", kategori))
        assertEquals(null, CategoryResolver.categoryMentionedIn("beli motor 20 juta", kategori))
    }
}
