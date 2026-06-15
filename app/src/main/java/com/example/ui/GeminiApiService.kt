package com.example.ui

import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiSchemaItem(
    val type: String,
    val description: String? = null,
    val nullable: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSchemaOptions(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchemaItem>? = null,
    val required: List<String>? = null,
    val nullable: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchemaOptions? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidateContent(
    val parts: List<GeminiPart>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiCandidateContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class ExtractionResult(
    val description: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val category: String? = null,
    val date: Long? = null,
    val notes: String? = null,
    val error: String? = null,
    val pertanyaan: String? = null,
    val emoji: String? = null
)

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @retrofit2.http.Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val BASE_URL = try {
        com.example.BuildConfig.GEMINI_BASE_URL.trim().replace("\"", "").ifEmpty { "https://your-cloudflare-worker-url.workers.dev/" }
    } catch (e: Throwable) {
        "https://your-cloudflare-worker-url.workers.dev/"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val cleanBase = if (BASE_URL.endsWith("/")) BASE_URL else "$BASE_URL/"
        val retrofit = Retrofit.Builder()
            .baseUrl(cleanBase)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    fun getFullUrl(path: String): String {
        val base = BASE_URL.trim()
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return cleanBase + cleanPath
    }
}
