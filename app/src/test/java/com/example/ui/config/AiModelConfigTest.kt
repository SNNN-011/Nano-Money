package com.example.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yang dikirim ke API harus id model yang benar-benar ada.
 *
 * Label di UI (`gemini-3.5-flash-lite`, `gemma-4-31b-it`) bukan id model Google,
 * jadi kalau dikirim apa adanya Google membalas 404. Label `gemma-*` juga ditolak
 * 403 oleh whitelist path di proxy Worker yang hanya mengizinkan `/models/gemini-`.
 */
class AiModelConfigTest {

    @Test
    fun `label_ui_dipetakan_ke_id_model_asli`() {
        assertEquals(AiModelConfig.GEMMA_WIRE_MODEL, AiModelConfig.wireModel(AiModelConfig.GEMMA_MODEL))
        assertEquals(AiModelConfig.GEMINI_WIRE_MODEL, AiModelConfig.wireModel(AiModelConfig.GEMINI_MODEL))
    }

    @Test
    fun `tidak_pernah_mengirim_label_palsu_apa_adanya`() {
        listOf(AiModelConfig.GEMINI_MODEL, AiModelConfig.GEMMA_MODEL).forEach { label ->
            val wire = AiModelConfig.wireModel(label)
            assertTrue("label '$label' tidak boleh jadi id wire", wire != label)
        }
    }

    @Test
    fun `semua_model_yang_dikirim_berawalan_gemini_supaya_lolos_whitelist_worker`() {
        listOf(AiModelConfig.GEMINI_MODEL, AiModelConfig.GEMMA_MODEL).forEach { label ->
            AiModelConfig.modelsToTry(label).forEach { model ->
                assertTrue(
                    "model '$model' akan ditolak 403 oleh Worker",
                    model.startsWith("gemini-")
                )
            }
        }
    }

    @Test
    fun `model_terpilih_dipercoba_lebih_dulu`() {
        val forGemma = AiModelConfig.modelsToTry(AiModelConfig.GEMMA_MODEL)
        val forGemini = AiModelConfig.modelsToTry(AiModelConfig.GEMINI_MODEL)

        assertEquals(AiModelConfig.GEMMA_WIRE_MODEL, forGemma.first())
        assertEquals(AiModelConfig.GEMINI_WIRE_MODEL, forGemini.first())
    }

    @Test
    fun `tidak_mengulang_model_yang_sama`() {
        AiModelConfig.modelsToTry(AiModelConfig.GEMMA_MODEL).forEach { model ->
            assertEquals(
                1,
                AiModelConfig.modelsToTry(AiModelConfig.GEMMA_MODEL).count { it == model }
            )
        }
    }

    @Test
    fun `label_tidak_dikenal_pakai_default_yang_aman`() {
        val models = AiModelConfig.modelsToTry("model-hantu")

        assertTrue(models.first().startsWith("gemini-"))
        // Default ikut masuk daftar cadangan, lalu di-distinct supaya tidak dicoba dua kali.
        assertEquals(models.distinct(), models)
        assertEquals(AiModelConfig.GEMMA_WIRE_MODEL, models.first())
    }

    @Test
    fun `timeout_percobaan_tidak_lama_banget`() {
        // 30 detik x beberapa model = layar menggantung hampir semenit.
        assertTrue(AiModelConfig.ATTEMPT_TIMEOUT_MS <= 15_000L)
    }
}
