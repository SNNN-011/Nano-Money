package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien tipis untuk TypeLLM (https://typellm.ai) — endpoint `POST /v1/generate`.
 *
 * Pakai OkHttp + org.json (bukan Retrofit/Moshi) karena payload `questions` adalah
 * map dinamis bertingkat yang tidak bisavik dengan generated adapter.
 *
 * TypeLLM tidak mendukung nested object/array pada output, jadi pemanggil harus
 * mendeklarasikan field flat satu per satu (lihat ReceiptParserUseCase).
 */
object TypeLLMClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Hasil pemanggilan: peta field yang sudah ter-parse, plus usage. */
    data class TypedResult(
        val result: Map<String, Any?>,
        val thinking: String? = null,
        val inputTokens: Int? = null,
        val thinkingTokens: Int? = null,
        /** Field yang dilewati server (mis. enum tak cocok). */
        val skipped: List<String> = emptyList(),
        val model: String? = null
    )

    sealed class TypeLlmException(message: String) : Exception(message) {
        class Unauthorized(msg: String) : TypeLlmException(msg)
        class RateLimited(msg: String) : TypeLlmException(msg)
        class BadRequest(msg: String) : TypeLlmException(msg)
        class Network(msg: String) : TypeLlmException(msg)
        class Unexpected(msg: String) : TypeLlmException(msg)
    }

    /**
     * Helper untuk menyusun satu definisi field (JSON Schema per field TypeLLM).
     *
     * @param nullable tambahkan "null" ke tipe agar field boleh bernilai null.
     */
    fun field(
        type: String,
        instructions: String,
        enumValues: List<Any>? = null,
        nullable: Boolean = false,
        dependsOn: List<String>? = null
    ): JSONObject {
        val obj = JSONObject()
        if (nullable) {
            val types = JSONArray().put(type).put("null")
            obj.put("type", types)
        } else {
            obj.put("type", type)
        }
        if (!enumValues.isNullOrEmpty()) {
            val arr = JSONArray()
            enumValues.forEach { arr.put(it) }
            obj.put("enum", arr)
        }
        if (!dependsOn.isNullOrEmpty()) {
            val arr = JSONArray()
            dependsOn.forEach { arr.put(it) }
            obj.put("depends_on", arr)
        }
        obj.put("instructions", instructions)
        return obj
    }

    /**
     * Kirim satu request `POST /v1/generate`.
     *
     * @param context teks konteks. Pada mode gambar, string ini boleh berisi
     *   instruksi ringkas karena gambar adalah sumber utama.
     * @param images data URI base64 (`data:image/jpeg;base64,...`), maks 8.
     * @throws TypeLlmException untuk kegagalan jaringan / HTTP non-2xx / body rusak.
     */
    suspend fun generate(
        apiKey: String,
        baseUrl: String,
        context: String,
        questions: JSONObject,
        images: List<String> = emptyList(),
        model: String? = null,
        temperature: Double = 0.0,
        seed: Int? = null,
        timeoutSeconds: Int = 60
    ): TypedResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw TypeLlmException.BadRequest(
                "API key TypeLLM belum dikonfigurasi. Tambahkan TYPELLM_API_KEY di local.properties."
            )
        }
        if (questions.length() == 0) {
            throw TypeLlmException.BadRequest("Pertanyaan (questions) tidak boleh kosong.")
        }

        val body = JSONObject().apply {
            put("context", context)
            put("questions", questions)
            if (images.isNotEmpty()) {
                val arr = JSONArray()
                images.forEach { arr.put(it) }
                put("images", arr)
            }
            if (!model.isNullOrBlank()) put("model", model)
            put("options", JSONObject().apply {
                put("temperature", temperature)
                if (seed != null) put("seed", seed)
            })
            put("timeout", timeoutSeconds)
        }

        val url = baseUrl.trimEnd('/') + "/v1/generate"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val serverMsg = runCatching {
                    JSONObject(raw).opt("error")?.toString()
                }.getOrNull()

                val detail = serverMsg?.takeIf { it.isNotBlank() && it != "null" } ?: raw.take(300)
                val msg = "TypeLLM HTTP ${response.code}: ${detail.ifBlank { response.message }}"
                throw when (response.code) {
                    401, 403 -> TypeLlmException.Unauthorized(msg)
                    429 -> TypeLlmException.RateLimited(msg)
                    in 400..499 -> TypeLlmException.BadRequest(msg)
                    else -> TypeLlmException.Unexpected(msg)
                }
            }

            val json = runCatching { JSONObject(raw) }
                .getOrElse { throw TypeLlmException.Unexpected("Respons TypeLLM bukan JSON valid.") }

            val resultObj = json.optJSONObject("result")
                ?: throw TypeLlmException.Unexpected("Respons TypeLLM tidak memuat field 'result'.")

            val flat = mutableMapOf<String, Any?>()
            for (key in resultObj.keys()) {
                val value = resultObj.get(key)
                flat[key] = if (value == JSONObject.NULL) null else value
            }

            // `thinking` dikembalikan server sebagai object (bukan string), dan
            // `usage` memakai input_tokens/thinking_tokens.
            val usage = json.optJSONObject("usage")
            val thinking = json.opt("thinking").let { raw ->
                when (raw) {
                    is String -> raw.ifBlank { null }
                    is JSONObject -> raw.keys().asSequence().toList().takeIf { it.isNotEmpty() }
                        ?.joinToString("\n") { k -> "$k: ${raw.opt(k)}" }
                    else -> null
                }
            }

            val skipped = mutableListOf<String>()
            json.optJSONArray("skipped")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (val s = arr.opt(i)) {
                        is String -> skipped.add(s)
                        is JSONObject -> s.optString("field").takeIf { it.isNotBlank() }?.let { skipped.add(it) }
                    }
                }
            }

            TypedResult(
                result = flat,
                thinking = thinking,
                inputTokens = usage?.optInt("input_tokens", 0)?.takeIf { it > 0 },
                thinkingTokens = usage?.optInt("thinking_tokens", 0)?.takeIf { it > 0 },
                skipped = skipped,
                model = json.optString("model", "").ifBlank { null }
            )
        }
    }
}