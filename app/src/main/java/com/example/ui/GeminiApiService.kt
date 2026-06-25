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
        val rawUrl = com.example.BuildConfig.GEMINI_BASE_URL
        val decryptedUrl = com.example.util.SecurityUtil.decryptObfuscatedString(rawUrl)
        val finalUrl = if (decryptedUrl.startsWith("http")) decryptedUrl else rawUrl.trim().replace("\"", "")
        
        if (finalUrl.isEmpty() || finalUrl.contains("your-cloudflare-worker-url")) {
            "https://generativelanguage.googleapis.com/"
        } else {
            finalUrl
        }
    } catch (e: Throwable) {
        "https://generativelanguage.googleapis.com/"
    }

    private val WORKER_SECRET_KEY = try {
        val rawSecret = com.example.BuildConfig.WORKER_SECRET_KEY
        val decryptedSecret = com.example.util.SecurityUtil.decryptObfuscatedString(rawSecret)
        
        // If decrypted secret contains unprintable characters, assume fallback to raw
        if (decryptedSecret.isNotEmpty() && !decryptedSecret.any { it.code < 32 }) {
            decryptedSecret
        } else {
            rawSecret.trim().replace("\"", "")
        }
    } catch (e: Throwable) {
        ""
    }

    private val BASE_HOST = try {
        java.net.URL(BASE_URL).host
    } catch (e: Throwable) {
        ""
    }

    private fun getDecryptedFallback(): String {
        val obfuscationKey = 42
        val obfuscated = intArrayOf(73, 69, 67, 4, 79, 82, 75, 67, 74, 70, 79, 4, 109, 79, 67, 115, 68, 115, 75, 74, 74)
        val sb = StringBuilder()
        for (x in obfuscated) {
            sb.append((x xor obfuscationKey).toChar())
        }
        return sb.toString()
    }

    private fun cleanString(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9+/=:-]"), "").trim()
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        connectTimeout(30, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(30, TimeUnit.SECONDS)
        addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
            
            if (WORKER_SECRET_KEY.isNotEmpty()) {
                requestBuilder.header("X-Worker-Secret", WORKER_SECRET_KEY)
            }
            
            try {
                val timestamp = System.currentTimeMillis().toString()
                val method = originalRequest.method
                val pathAndQuery = originalRequest.url.encodedPath + 
                        (originalRequest.url.encodedQuery?.let { "?$it" } ?: "")
                
                val bodyString = if (originalRequest.body != null) {
                    val buffer = okio.Buffer()
                    originalRequest.body?.writeTo(buffer)
                    buffer.readUtf8()
                } else {
                    ""
                }
                
                val messageToSign = "$timestamp\n$method\n$pathAndQuery\n$bodyString"
                val signingKey = WORKER_SECRET_KEY.ifEmpty { getDecryptedFallback() }
                
                val keyBytes = signingKey.toByteArray(Charsets.UTF_8)
                val hmacSha256 = javax.crypto.Mac.getInstance("HmacSHA256")
                val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256")
                java.util.Arrays.fill(keyBytes, 0.toByte())
                
                hmacSha256.init(secretKey)
                val signatureBytes = hmacSha256.doFinal(messageToSign.toByteArray(Charsets.UTF_8))
                val signature = android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP)
                
                requestBuilder.header("X-Signature-Timestamp", timestamp)
                requestBuilder.header("X-Signature", signature)
            } catch (e: Throwable) {
                android.util.Log.e("GeminiClient", "Failed to sign request", e)
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
        
        // If calling the direct standard Google Gemini API, map unsupported mockup models to standard ones
        if (BASE_URL.contains("googleapis.com")) {
            if (cleanPath.contains("models/gemma-4-31b-it") || cleanPath.contains("models/gemini-3.1-flash-lite") || cleanPath.contains("models/gemini-2.0-flash-lite")) {
                cleanPath = cleanPath
                    .replace("models/gemma-4-31b-it", "models/gemini-1.5-flash")
                    .replace("models/gemini-3.1-flash-lite", "models/gemini-1.5-flash")
                    .replace("models/gemini-2.0-flash-lite", "models/gemini-1.5-flash")
            }
        }
        
        val base = BASE_URL.trim()
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        return cleanBase + cleanPath
    }
}
