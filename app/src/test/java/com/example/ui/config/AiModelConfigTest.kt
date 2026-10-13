package com.example.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat memakai satu model dengan cadangan berurutan.
 *
 * Nama model di sini dibaca apa adanya oleh proxy Worker (lihat `modelMapping` di
 * `cloudflare-worker/src/worker.js`), jadi aplikasi tidak boleh memetakannya ulang ke
 * id model lain. Worker juga sudah punya fallback sendiri; ladder di aplikasi
 * ada sebagai jaring pengaman kedua.
 *exists sebagai jaring pengaman kedua.
 */
class AiModelConfigTest {

    @Test
    fun `urutan_pencobaan_sesuai_yang_diminta`() {
        assertEquals(
            listOf("gemma-4-31b-it", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"),
            AiModelConfig.modelsToTry()
        )
    }

    @Test
    fun `model_utama_dipakai_tidak_ada_pilihan_lain_di_ui`() {
        // Tidak ada lagi GEMMA_MODEL/GEMINI_MODEL sebagai pilihan; ladder tetap satu jalur.
        assertEquals(AiModelConfig.PRIMARY_MODEL, AiModelConfig.modelsToTry().first())
        assertEquals(AiModelConfig.PRIMARY_MODEL, "gemma-4-31b-it")
        assertEquals(AiModelConfig.FALLBACK_MODEL_1, "gemini-3.1-flash-lite")
        assertEquals(AiModelConfig.FALLBACK_MODEL_2, "gemini-3.5-flash-lite")
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
    fun `reader_struk_mulai_dari_model_yang_lebih_kuat_lalu_cadangan`() {
        assertEquals(
            listOf(AiModelConfig.FALLBACK_MODEL_2, AiModelConfig.PRIMARY_MODEL),
            AiModelConfig.RECEIPT_MODELS
        )
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
    fun `timeout_percobaan_tidak_lama_banget`() {
        // 30 detik x beberapa model = layar menggantung hampir semenit.
        assertTrue(AiModelConfig.ATTEMPT_TIMEOUT_MS <= 15_000L)
    }
}
