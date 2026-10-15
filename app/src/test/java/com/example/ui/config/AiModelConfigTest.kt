package com.example.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App mengirim SATU model saja; fallback berantai adalah tugas Worker.
 *
 * Nama model dibaca apa adanya oleh proxy Worker (lihat `MODEL_CHAINS` di
 * `cloudflare-worker/src/worker.js`), jadi aplikasi tidak boleh memetakannya ulang
 * ke id model lain.
 */
class AiModelConfigTest {

    @Test
    fun `app_hanya_mengirim_satu_model_sisanya_diserahkan_ke_worker`() {
        assertEquals(listOf(AiModelConfig.PRIMARY_MODEL), AiModelConfig.modelsToTry())
        assertEquals(1, AiModelConfig.modelsToTry().size)
    }

    @Test
    fun `model_tercepat_yang_dijadi_utama_bukan_yang_lama_menggantung`() {
        // Pengukuran latency: gemma-4-31b-it hanging 40 detik lalu 500,
        // gemini-3.1-flash-lite selalu 200 dalam ~3 detik.
        assertEquals("gemini-3.1-flash-lite", AiModelConfig.PRIMARY_MODEL)
        assertEquals("gemma-4-31b-it", AiModelConfig.FALLBACK_MODEL_1)
        assertEquals("gemini-3.5-flash-lite", AiModelConfig.FALLBACK_MODEL_2)
    }

    @Test
    fun `tidak_mengulang_model_yang_sama`() {
        val models = AiModelConfig.modelsToTry()
        assertEquals(models.distinct(), models)
    }

    @Test
    fun `nama_model_tidak_kosong_dan_tanpa_spasi_aneh`() {
        (AiModelConfig.modelsToTry() + AiModelConfig.RECEIPT_MODELS).forEach { model ->
            assertTrue("nama model kosong", model.isNotBlank())
            assertEquals("nama model tidak boleh punya spasi", model.trim(), model)
        }
    }

    @Test
    fun `reader_struk_mulai_dari_model_yang_paling_andal`() {
        // gemini-3.5-flash-lite sempat membalas 503, jadi tidak boleh di posisi pertama.
        assertEquals(
            listOf(
                AiModelConfig.PRIMARY_MODEL,
                AiModelConfig.FALLBACK_MODEL_2,
                AiModelConfig.FALLBACK_MODEL_1
            ),
            AiModelConfig.RECEIPT_MODELS
        )
    }

    @Test
    fun `nama_model_semuanya_ada_di_daftar_model_google_yang_diketahui`() {
        // Divalidasi terhadap respons /v1beta/models milik kunci API. Kalau nama
        // salah ketik, Gemini membalas 404 dan seluruh request gagal.
        val dikenal = setOf(
            "gemma-4-31b-it",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite"
        )
        (AiModelConfig.CHAT_MODELS + AiModelConfig.RECEIPT_MODELS).forEach { model ->
            assertTrue("model '$model' tidak ada di daftar model Google", model in dikenal)
        }
    }

    @Test
    fun `semua_nama_model_lolos_ke_worker_sebagai_model_path`() {
        // Worker mem-parsing /v1beta/models/<nama>:<action>; nama harus tidak kosong.
        (AiModelConfig.modelsToTry() + AiModelConfig.RECEIPT_MODELS).forEach { model ->
            assertTrue(
                "path /v1beta/models/$model tidak valid",
                model.matches(Regex("[A-Za-z0-9._-]+"))
            )
        }
    }

    @Test
    fun `timeout_percobaan_cukup_untuk_rantai_worker_tapi_tetap_terbatas`() {
        // Worker: 8 detik per panggilan x 2 percobaan, plus perpindahan model.
        // Jadi batas aplikasi harus di atas itu, tapi tidak boleh 30 detik lagi
        // seperti dulu karena itu membuat layar menggantung.
        assertTrue(AiModelConfig.ATTEMPT_TIMEOUT_MS >= 18_000L)
        assertTrue(AiModelConfig.ATTEMPT_TIMEOUT_MS <= 25_000L)
    }
}
