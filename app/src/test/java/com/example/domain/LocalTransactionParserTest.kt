package com.example.domain

import com.example.data.local.LocalEngine
import com.example.data.remote.ExtractionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Terjemahan function call model lokal -> ExtractionResult.
 *
 * Modelnya sendiri diuji di VM (vm/eval_app.py); yang diuji di sini adalah
 * lapisan aplikasi: apakah balasan mesin diterjemahkan ke bentuk yang dipakai
 * dialog konfirmasi, tanpa mengarang angka dan tanpa memakai tanggal dari model.
 *
 * Robolectric dipakai karena penerjemahnya membaca org.json -- di unit test JVM
 * biasa, kelas itu cuma stub Android yang langsung melempar RuntimeException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalTransactionParserTest {

    private val incomeCategories = listOf("Gaji", "Bonus", "Freelance", "Lainnya")
    private val expenseCategories = listOf("Makanan", "Transportasi", "Tagihan", "Lainnya")

    /** Mesin palsu: mengembalikan balasan siap pakai, sekaligus merekam teksnya. */
    private class FakeEngine(private val reply: String) : LocalEngine {
        var lastText: String? = null
        var lastMaxTokens: Int? = null

        override suspend fun complete(text: String, maxNewTokens: Int): String {
            lastText = text
            lastMaxTokens = maxNewTokens
            return reply
        }
    }

    private class FailingEngine : LocalEngine {
        override suspend fun complete(text: String, maxNewTokens: Int): String =
            throw IllegalStateException("mesin lokal mati")
    }

    private fun envelope(calls: String): String =
        """{"type":"call","function_calls":[$calls]}"""

    private fun catat(
        name: String,
        amount: String,
        category: String,
        item: String? = null
    ): String {
        val itemPart = if (item == null) "" else ""","item":"$item""""
        return """{"name":"$name","arguments":{"amount":$amount,"category":"$category"$itemPart}}"""
    }

    private fun parse(
        engine: LocalEngine,
        text: String,
        pending: String? = null,
        nowMs: Long = 1_700_000_000_000L
    ): RequestResult<ExtractionResult> = runBlocking {
        LocalTransactionParser(engine, clock = { nowMs })
            .parseTransaction(text, pending, incomeCategories, expenseCategories)
    }

    private fun success(result: RequestResult<ExtractionResult>): ExtractionResult {
        assertTrue("harus sukses, dapat: $result", result is RequestResult.Success)
        return (result as RequestResult.Success).data
    }

    @Test
    fun `pengeluaran_diterjemahkan_lengkap`() {
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "25000", "makanan", "bakso")))
        val result = success(parse(engine, "beli bakso 25 ribu"))

        assertEquals("Bakso", result.description)
        assertEquals(25_000L, result.amount)
        assertEquals("PENGELUARAN", result.type)
        assertEquals("makanan", result.category)
        assertEquals("nominal terbaca", null, result.error)
    }

    @Test
    fun `uang_masuk_tidak_disebut_pengeluaran`() {
        val engine = FakeEngine(envelope(catat("catat_masuk", "5000000", "gaji")))
        val result = success(parse(engine, "gajian 5 juta"))

        assertEquals("PEMASUKAN", result.type)
        assertEquals(5_000_000L, result.amount)
    }

    @Test
    fun `tanggal_selalu_hari_ini_bukan_dari_model`() {
        val engine = FakeEngine(
            """{"type":"call","function_calls":[{"name":"catat_pengeluaran","arguments":""" +
                """{"amount":10000,"category":"makanan","date":"2020-01-01"}}]}"""
        )
        val result = success(parse(engine, "kopi 10 ribu kemarin", nowMs = 1_700_000_000_000L))

        assertEquals(1_700_000_000_000L, result.date)
    }

    @Test
    fun `minta_nominal_diteruskan_sebagai_pertanyaan`() {
        val engine = FakeEngine(envelope("""{"name":"tanya","arguments":{"field":"amount"}}"""))
        val result = success(parse(engine, "bayar iBox"))

        assertEquals("klarifikasi", result.error)
        assertNotNull(result.pertanyaan)
        assertTrue("pertanyaan harus menyinggung nominal: ${result.pertanyaan}", result.pertanyaan!!.contains("ominal"))
    }

    @Test
    fun `minta_jenis_transaksi_diteruskan_sebagai_pertanyaan`() {
        val engine = FakeEngine(envelope("""{"name":"tanya","arguments":{"field":"type"}}"""))
        val result = success(parse(engine, "uang 200 ribu masuk"))

        assertEquals("klarifikasi", result.error)
        assertNotNull(result.pertanyaan)
    }

    @Test
    fun `tanpa_function_call_dianggap_bukan_transaksi`() {
        val engine = FakeEngine("""{"type":"text","function_calls":[]}""")
        val result = success(parse(engine, "apa kabar bot?"))

        assertEquals("bukan_transaksi", result.error)
    }

    @Test
    fun `tanpa_panggilan_tapi_nominalnya_jelas_ditanya_jenisnya`() {
        // "uang 200 ribu": nominalnya jelas, arah uangnya tidak. Menolaknya sebagai
        // bukan-transaksi itu salah -- user cuma perlu ditanya satu hal.
        val engine = FakeEngine("""{"type":"text","function_calls":[]}""")
        val result = success(parse(engine, "uang 200 ribu"))

        assertEquals("klarifikasi", result.error)
        assertNotNull(result.pertanyaan)
        assertTrue(
            "pertanyaan harus menyinggung arah uang: ${result.pertanyaan}",
            result.pertanyaan!!.contains("masuk")
        )
    }

    @Test
    fun `tanpa_panggilan_dan_tanpa_nominal_tetap_bukan_transaksi`() {
        val engine = FakeEngine("""{"type":"text","function_calls":[]}""")
        val result = success(parse(engine, "apa kabar hari ini?"))

        assertEquals("bukan_transaksi", result.error)
    }

    @Test
    fun `panggilan_yang_ditahan_mesin_cuma_dipakai_untuk_bertanya`() {
        // Mesin menahan panggilan yang keyakinannya rendah. Isinya tidak boleh
        // disimpan -- nominal dan kategorinya justru yang paling tidak tepercaya.
        // Kehadirannya cuma cukup jadi alasan menanyakan nominal.
        val engine = FakeEngine(
            """{"type":"call","function_calls":[],"suppressed_calls":""" +
                """[{"name":"catat_pengeluaran","arguments":{"amount":5000,"category":"makanan"}}]}"""
        )
        val result = success(parse(engine, "bayar iBox"))

        assertEquals("klarifikasi", result.error)
        assertNotNull(result.pertanyaan)
        assertTrue(
            "pertanyaan harus menyinggung nominal: ${result.pertanyaan}",
            result.pertanyaan!!.contains("ominal")
        )
        assertEquals("nominal ditahan tidak boleh ikut tersimpan", null, result.amount)
        assertEquals("kategori ditahan tidak boleh ikut tersimpan", null, result.category)
    }

    @Test
    fun `nominal_terbaca_menang_atas_panggilan_yang_ditahan_mesin`() {
        // Dua-duanya ada: yang ditanya arah uangnya, bukan nominalnya, karena
        // kalimat user sudah menyebut nominalnya sendiri.
        val engine = FakeEngine(
            """{"type":"call","function_calls":[],"suppressed_calls":""" +
                """[{"name":"catat_masuk","arguments":{"amount":200000,"category":"lainnya"}}]}"""
        )
        val result = success(parse(engine, "uang 200 ribu"))

        assertEquals("klarifikasi", result.error)
        assertTrue(
            "pertanyaan harus menyinggung arah uang: ${result.pertanyaan}",
            result.pertanyaan!!.contains("masuk")
        )
    }

    @Test
    fun `nominal_hilang_ditanyakan_bukan_dikarang`() {
        val engine = FakeEngine(envelope("""{"name":"catat_pengeluaran","arguments":{"category":"makanan"}}"""))
        val result = success(parse(engine, "beli bakso"))

        assertEquals("klarifikasi", result.error)
        assertTrue("pertanyaan harus menyinggung nominal: ${result.pertanyaan}", result.pertanyaan!!.contains("ominal"))
    }

    @Test
    fun `nominal_sebagai_teks_tetap_terbaca`() {
        val engine = FakeEngine(
            envelope("""{"name":"catat_pengeluaran","arguments":{"amount":"15000.0","category":"makanan"}}""")
        )
        val result = success(parse(engine, "nasi goreng 15 ribu"))

        assertEquals(15_000L, result.amount)
    }

    @Test
    fun `jawaban_lanjutan_digabung_jadi_satu_kalimat`() {
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "30000", "makanan", "bakso")))
        parse(engine, "30 ribu", pending = "beli bakso")

        assertEquals("beli bakso 30 ribu", engine.lastText)
        assertEquals(128, engine.lastMaxTokens)
    }

    @Test
    fun `balasan_rusak_jadi_pesan_error_bukan_crash`() {
        val engine = FakeEngine("bukan json sama sekali")
        val result = parse(engine, "beli bakso 25 ribu")

        assertTrue("harus Error, dapat: $result", result is RequestResult.Error)
    }

    @Test
    fun `mesin_mati_jadi_pesan_error`() {
        val result = parse(FailingEngine(), "beli bakso 25 ribu")

        assertTrue("harus Error, dapat: $result", result is RequestResult.Error)
    }

    @Test
    fun `kategori_pemasukan_milik_user_membetulkan_jenis`() {
        // Model menandai pengeluaran padahal kategorinya sendiri kategori pemasukan.
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "5000000", "Gaji")))
        val result = success(parse(engine, "gaji bulan ini 5 juta"))

        assertEquals("PEMASUKAN", result.type)
    }

    @Test
    fun `teks_panjang_dipotong_sebelum_dikirim`() {
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "1000", "makanan")))
        parse(engine, "a".repeat(900))

        assertEquals(500, engine.lastText!!.length)
    }

    @Test
    fun `nominal_dari_kalimat_user_menang_atas_angka_model`() {
        // Terukur di app_path_probe.py: model membalas "2 juta" sebagai 2000.
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "2000", "lainnya", "sewa")))
        val result = success(parse(engine, "sewa kos 2 juta"))

        assertEquals(2_000_000L, result.amount)
    }

    @Test
    fun `tanpa_nominal_di_kalimat_angka_model_yang_dipakai`() {
        // "tanggal 28" bukan nominal; di kalimat seperti ini angka model dipakai.
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "45000", "makanan", "nasi goreng")))
        val result = success(parse(engine, "tanggal 28 nasi goreng"))

        assertEquals(45_000L, result.amount)
    }

    @Test
    fun `jawaban_klarifikasi_nominal_ikut_terbaca`() {
        // Model menanyakan nominal ("bayar iBox"), user menjawab "3 juta".
        // Jawabannya digabung ke kalimat awal, lalu nominalnya dibaca dari situ.
        val engine = FakeEngine(envelope(catat("catat_pengeluaran", "3000", "lainnya", "iBox")))
        val result = success(parse(engine, "3 juta", pending = "bayar iBox"))

        assertEquals(3_000_000L, result.amount)
    }
}
