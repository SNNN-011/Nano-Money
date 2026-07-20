package com.example.data.remote

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
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks

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
        val rawUrl = com.example.BuildConfig.GEMINI_BASE_URL
        val finalUrl = rawUrl.trim().replace("\"", "")

        if (finalUrl.isEmpty() || finalUrl.contains("your-cloudflare-worker-url")) {
            "https://generativelanguage.googleapis.com/"
        } else {
            finalUrl
        }
    } catch (e: Throwable) {
        "https://generativelanguage.googleapis.com/"
    }

    val isUsingProxy: Boolean
        get() = !BASE_URL.contains("googleapis.com")

    // Cached token to avoid runBlocking in interceptor
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiryMs: Long = 0L
    private const val TOKEN_CACHE_TTL_MS = 50_000L // 50 seconds — Firebase tokens last 60min, this prevents stale on refresh

    /**
     * Get cached token or fetch synchronously. Called from OkHttp interceptor thread.
     * Uses Tasks.await() (Google Play Services pattern) instead of runBlocking.
     */
    private fun getCachedOrFetchToken(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { token ->
            if (now < tokenExpiryMs) return token
        }
        return try {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return null
            val result = com.google.android.gms.tasks.Tasks.await(user.getIdToken(false))
            val token = result.token
            if (token != null) {
                cachedToken = token
                tokenExpiryMs = now + TOKEN_CACHE_TTL_MS
            }
            token
        } catch (e: Exception) {
            com.example.util.SecureLog.w("GeminiClient", "Failed to fetch Firebase token", e)
            null
        }
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        connectTimeout(30, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(30, TimeUnit.SECONDS)
        addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            if (isUsingProxy) {
                val token = getCachedOrFetchToken()
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            chain.proceed(requestBuilder.build())
        }
    }.build()

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
        var cleanPath = if (path.startsWith("/")) path.substring(1) else path

        val base = BASE_URL.trim()
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        return cleanBase + cleanPath
    }
}
