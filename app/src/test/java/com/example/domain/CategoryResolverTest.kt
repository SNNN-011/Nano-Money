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
 * di sini. Tidak ada cara menebak bahwa "Hiburan"_author maksudnya "Game" tanpa
 * salah juga di kasus lain. Itu dicegah lewat prompt, bukan lewat pencocokan.
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
    fun `entri_kosong_di_daftar_tidak_pernah_jadi_hasil_pencocokan`() {
        // Nama kategori yang berisi spasi saja akan ternormalisasi jadi kosong.
        // Kalau tidak dilewati, kategori pertama yang kosong itu yang terkirim balik
        // dan transaksi tersimpan tanpa kategori.
        assertEquals("Game", harusCocok("Game", listOf("", "   ", "Game", "Makanan")))
    }
}
