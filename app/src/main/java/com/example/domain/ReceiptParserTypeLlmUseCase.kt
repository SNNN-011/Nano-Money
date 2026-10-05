package com.example.domain

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.remote.TypeLLMClient
import com.example.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Parser struk berbasis TypeLLM (https://typellm.ai).
 *
 * CATATAN DESAIN PENTING
 * ----------------------
 * TypeLLM tidak mendukung nested object maupun array pada output
 * ("Nested objects and arrays are unsupported"), padahal struk berisi daftar item.
 * Solusinya adalah parse 2 tahap:
 *
 *   Tahap 1 — satu call: ambil `title`, `date`, `is_receipt`, dan `item_count`.
 *   Tahap 2 — satu call: karena `item_count` sudah diketahui, kita bisa
 *             mendeskripsikan tepat satu field per item (name/amount/category/
 *             emoji/type) dan semuanya dievaluasi paralel oleh TypeLLM.
 *
 * Batasan lain yang sudah ditangani di sini:
 *  - `enum` maksimum 24 nilai  -> daftar kategori user dipotong ke 23 + "Lainnya".
 *  - jawaban string maksimum 128 token -> instruksi singkat, bukan teks panjang.
 *  - gambar dikirim sebagai data URI base64, maksimal 8.
 */
class ReceiptParserTypeLlmUseCase {

    private data class Detection(
        val title: String,
        val dateMillis: Long,
        val itemCount: Int,
        val isReceipt: Boolean
    )

    /** Emoji yang diizinkan; dipakai sebagai enum agar hasil selalu valid. */
    private val emojiEnum = listOf(
        "🍔", "🍜", "☕", "🛒", "🥬", "🍱", "🧾", "💊", "🏥", "🚗", "⛽", "🛵",
        "👕", "👟", "💄", "🧴", "🎮", "📱", "💻", "🎁", "🐱", "🏠", "📦", "🔄"
    )

    private companion object {
        /** Batas keras enum TypeLLM. 1 slot dipakai untuk "Lainnya". */
        const val MAX_ENUM_VALUES = 24
        /** Batas keras questions per request (dari API reference). */
        const val MAX_QUESTIONS_PER_CALL = 64
        /** Field per item: name, amount, category, emoji, type. */
        const val FIELDS_PER_ITEM = 5
        /**
         * Batas item per SESI (bukan per request). Satu struk dengan banyak item
         * dipecah menjadi beberapa request karena batas 64 questions per call.
         */
        const val MAX_ITEMS = 40
        /** Padding nama kategori agar jumlah kategori + Lainnya <= 24. */
        const val CATEGORY_SLOTS = MAX_ENUM_VALUES - 1

        /** Item per batch: (64 / 5) = 12, sisa 4 field dipakai sebagai konteks. */
        const val ITEMS_PER_BATCH = (MAX_QUESTIONS_PER_CALL - 4) / FIELDS_PER_ITEM
    }

    private fun Bitmap.toJpegDataUri(quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    /**
     * @param expenseCategories daftar kategori aktif milik user (bisa kosong).
     */
    suspend fun parseReceipt(
        apiKey: String,
        baseUrl: String,
        bitmap: Bitmap,
        expenseCategories: List<String>
    ): RequestResult<ParsedReceipt> = withContext(Dispatchers.IO) {
        try {
            val imageDataUri = bitmap.toJpegDataUri()

            // ---- Tahap 1: deteksi metadata + jumlah item ----
            val detection = detectMetadata(
                apiKey = apiKey,
                baseUrl = baseUrl,
                imageDataUri = imageDataUri
            ) ?: return@withContext notAReceipt()

            if (!detection.isReceipt || detection.itemCount <= 0) {
                return@withContext notAReceipt()
            }

            // ---- Tahap 2: ekstrak detail per item ----
            val items = extractItems(
                apiKey = apiKey,
                baseUrl = baseUrl,
                imageDataUri = imageDataUri,
                title = detection.title,
                dateString = formatDateIso(detection.dateMillis),
                itemCount = detection.itemCount,
                expenseCategories = expenseCategories
            )

            if (items.isEmpty()) return@withContext notAReceipt()

            val calculatedTotal = items.sumOf { item ->
                if (item.type == "income") -item.amount else item.amount
            }

            RequestResult.Success(
                ParsedReceipt(
                    title = detection.title,
                    items = items,
                    grouped = groupByCategory(items),
                    total = calculatedTotal,
                    dateMillis = detection.dateMillis
                )
            )
        } catch (e: TypeLLMClient.TypeLlmException) {
            SecureLog.e("ReceiptParserTypeLlm", "Gagal menganalisis struk via TypeLLM", e)
            RequestResult.Error(userFacingMessage(e), e)
        } catch (e: Exception) {
            SecureLog.e("ReceiptParserTypeLlm", "Gagal tak terduga saat parsing struk", e)
            RequestResult.Error("Gagal menganalisis struk. Silakan coba lagi.", e)
        }
    }

    // ---------------------------------------------------------------------
    // Tahap 1
    // ---------------------------------------------------------------------

    private suspend fun detectMetadata(
        apiKey: String,
        baseUrl: String,
        imageDataUri: String
    ): Detection? {
        val questions = org.json.JSONObject().apply {
            put(
                "title",
                TypeLLMClient.field(
                    type = "string",
                    instructions = "Nama toko, merchant, atau aplikasi yang tertera. Huruf kapital di awal tiap kata. Maksimal 5 kata. Kalau tidak terbaca, tulis 'Struk Belanja'."
                )
            )
            put(
                "date",
                TypeLLMClient.field(
                    type = "string",
                    nullable = true,
                    instructions = "Tanggal transaksi format YYYY-MM-DD. Null jika tidak ada."
                )
            )
            put(
                "item_count",
                TypeLLMClient.field(
                    type = "integer",
                    instructions = "Jumlah baris barang atau rincian transaksi pada bukti. Maksimal 30. Null jika tidak terhitung."
                )
            )
            put(
                "is_receipt",
                TypeLLMClient.field(
                    type = "boolean",
                    instructions = "True hanya jika gambar benar-benar bukti transaksi (struk, invoice, resi, atau screenshot e-commerce/e-wallet). False untuk foto lain."
                )
            )
        }

        val context = "Analisis gambar bukti transaksi berikut. Gambar adalah sumber utama."
        val response = TypeLLMClient.generate(
            apiKey = apiKey,
            baseUrl = baseUrl,
            context = context,
            questions = questions,
            images = listOf(imageDataUri),
            timeoutSeconds = 60
        )

        val isReceipt = response.result["is_receipt"] as? Boolean ?: false
        val rawCount = (response.result["item_count"] as? Number)?.toInt() ?: 0
        val count = rawCount.coerceIn(0, MAX_ITEMS)
        val title = (response.result["title"] as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "Struk Belanja" }
            ?: "Struk Belanja"

        return Detection(
            title = title,
            dateMillis = parseDate(response.result["date"] as? String),
            itemCount = count,
            isReceipt = isReceipt
        )
    }

    // ---------------------------------------------------------------------
    // Tahap 2
    // ---------------------------------------------------------------------

    /**
     * Ekstrak detail tiap item.
     *
     * Item dipecah jadi beberapa batch karena API membatasi 64 questions per
     * request. Setiap batch mengirim `start_item`/`end_item` supaya model tahu
     * rentang baris mana yang diminta (dibantu konteks teks).
     */
    private suspend fun extractItems(
        apiKey: String,
        baseUrl: String,
        imageDataUri: String,
        title: String,
        dateString: String,
        itemCount: Int,
        expenseCategories: List<String>
    ): List<ReceiptItem> {
        val categoryEnum = buildCategoryEnum(expenseCategories)
        val validCategories = categoryEnum.map { it.toString() }
        val items = mutableListOf<ReceiptItem>()

        var start = 1
        while (start <= itemCount) {
            val end = minOf(start + ITEMS_PER_BATCH - 1, itemCount)

            val questions = org.json.JSONObject().apply {
                put("start_item", TypeLLMClient.field(
                    type = "integer",
                    instructions = "Nomor baris pertama yang diminta. Balas dengan angka yang sama."
                ))
                put("end_item", TypeLLMClient.field(
                    type = "integer",
                    instructions = "Nomor baris terakhir yang diminta. Balas dengan angka yang sama."
                ))
                put("batch_total", TypeLLMClient.field(
                    type = "number",
                    enumValues = listOf(0.0, 0.5, 1.0),
                    instructions = "Seberapa yakin seluruh baris dalam rentas ini terbaca lengkap?"
                ))

                for (idx in start..end) {
                    put("item${idx}_name", TypeLLMClient.field(
                        type = "string",
                        instructions = "Nama produk/barang spesifik pada baris ke-$idx dari bukti. Bukan nama toko. Maksimal 6 kata."
                    ))
                    put("item${idx}_amount", TypeLLMClient.field(
                        type = "number",
                        instructions = "Harga akhir baris ke-$idx dalam Rupiah, sudah setelah diskon. Nilai positif."
                    ))
                    put("item${idx}_category", TypeLLMClient.field(
                        type = "string",
                        enumValues = categoryEnum,
                        instructions = "Kategori baris ke-$idx. Pilih nilai paling cocok dari daftar."
                    ))
                    put("item${idx}_emoji", TypeLLMClient.field(
                        type = "string",
                        enumValues = emojiEnum,
                        instructions = "Satu emoji yang merepresentasikan baris ke-$idx."
                    ))
                    put("item${idx}_type", TypeLLMClient.field(
                        type = "string",
                        enumValues = listOf("expense", "income"),
                        instructions = "Baris ke-$idx: 'expense' untuk pengeluaran, 'income' untuk pendapatan (refund, cashback, saldo masuk)."
                    ))
                }
            }

            val context = buildString {
                append("Bukti transaksi dari: ").append(title)
                append(". Tanggal: ").append(dateString).append('.')
                append(" Total baris pada struk: ").append(itemCount).append('.')
                append(" Ekstrak HANYA baris ").append(start).append(" sampai ").append(end).append('.')
                append(" Untuk baris di luar rentas itu, jangan isi.")
            }

            val response = TypeLLMClient.generate(
                apiKey = apiKey,
                baseUrl = baseUrl,
                context = context,
                questions = questions,
                images = listOf(imageDataUri),
                timeoutSeconds = 90
            )

            for (idx in start..end) {
                val name = (response.result["item${idx}_name"] as? String)?.trim().orEmpty()
                val amount = (response.result["item${idx}_amount"] as? Number)?.toLong() ?: 0L

                // Abaikan baris yang tidak terbaca agar tidak mengotori transaksi.
                if (name.isBlank() || amount <= 0L) continue

                val rawCategory = (response.result["item${idx}_category"] as? String)?.trim()
                val category = rawCategory
                    ?.takeIf { validCategories.contains(it) }
                    ?: "Lainnya"

                val rawEmoji = (response.result["item${idx}_emoji"] as? String)?.trim()
                val emoji = rawEmoji?.takeIf { emojiEnum.contains(it) } ?: "📦"

                val rawType = (response.result["item${idx}_type"] as? String)?.trim()?.lowercase(Locale.ROOT)
                val type = if (rawType == "income" || rawType == "pemasukan") "income" else "expense"

                items.add(
                    ReceiptItem(
                        name = name,
                        amount = amount,
                        category = category,
                        emoji = emoji,
                        type = type
                    )
                )
            }

            start = end + 1
        }

        return items
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    /**
     * Susun daftar enum kategori: kategori milik user lebih dulu, lalu "Lainnya".
     * Dibatasi [CATEGORY_SLOTS] + 1 nilai agar tidak melewati batas 24.
     */
    private fun buildCategoryEnum(expenseCategories: List<String>): List<String> {
        val cleaned = expenseCategories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.substringBefore(' ') } // buang emoji/raw icon bila ada
            .distinct()

        val selected = cleaned.take(CATEGORY_SLOTS)
        return if (selected.contains("Lainnya")) selected else selected + "Lainnya"
    }

    /** Samakan dengan logika pengelompokan pada ReceiptParserUseCase (Gemini). */
    private fun groupByCategory(items: List<ReceiptItem>): List<GroupedReceiptTransaction> {
        val groupedMap = mutableMapOf<String, MutableList<ReceiptItem>>()
        for (item in items) {
            groupedMap.getOrPut("${item.category}|${item.type}") { mutableListOf() }.add(item)
        }

        return groupedMap.map { (catType, groupItems) ->
            val (category, type) = catType.split("|", limit = 2)
            val names = groupItems.map { it.name }
            val description = names.take(3).joinToString(", ") +
                if (names.size > 3) ", dll" else ""

            GroupedReceiptTransaction(
                category = category,
                description = description,
                amount = groupItems.sumOf { it.amount },
                emoji = groupItems.firstOrNull { it.emoji.isNotBlank() }?.emoji ?: "📦",
                type = type
            )
        }
    }

    private fun notAReceipt(): RequestResult<ParsedReceipt> = RequestResult.Success(
        ParsedReceipt(
            title = "",
            items = emptyList(),
            grouped = emptyList(),
            total = 0L,
            dateMillis = System.currentTimeMillis(),
            error = "bukan_struk"
        )
    )

    private fun userFacingMessage(e: TypeLLMClient.TypeLlmException): String = when (e) {
        is TypeLLMClient.TypeLlmException.Unauthorized ->
            "API key TypeLLM tidak valid atau belum punya akses. Periksa TYPELLM_API_KEY di local.properties."
        is TypeLLMClient.TypeLlmException.RateLimited ->
            "Batas request TypeLLM tercapai. Coba lagi nanti."
        is TypeLLMClient.TypeLlmException.BadRequest -> e.message
            ?: "Permintaan ditolak TypeLLM."
        is TypeLLMClient.TypeLlmException.Network ->
            "Tidak bisa menghubungi TypeLLM. Periksa koneksi internet."
        is TypeLLMClient.TypeLlmException.Unexpected ->
            "TypeLLM memberikan respons yang tidak terduga."
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.trim())
            parsed?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatDateIso(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(millis))
}