package com.example.domain

import android.graphics.Bitmap
import android.util.Base64
import com.example.ui.GeminiClient
import com.example.ui.GeminiContent
import com.example.ui.GeminiGenerationConfig
import com.example.ui.GeminiPart
import com.example.ui.GeminiRequest
import com.example.ui.InlineData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ReceiptItem(
    val name: String,
    val amount: Double,
    val category: String,
    val emoji: String = "📦",
    val type: String = "expense"
)

data class GroupedReceiptTransaction(
    val category: String,
    val description: String,
    val amount: Double,
    val emoji: String = "📦",
    val type: String = "expense"
)

data class ParsedReceipt(
    val title: String,
    val items: List<ReceiptItem>,
    val grouped: List<GroupedReceiptTransaction>,
    val total: Double,
    val dateMillis: Long,
    val error: String? = null
)

class ReceiptParserUseCase {

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress to JPEG with 80% quality to keep payload reasonably small and fast
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun parseReceipt(
        apiKey: String,
        bitmap: Bitmap,
        expenseCategories: List<String>
    ): RequestResult<ParsedReceipt> = withContext(Dispatchers.IO) {
        try {
            val base64Data = bitmap.toBase64()
            val categoriesStr = expenseCategories.map { com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second }.filter { it.isNotBlank() }.distinct().joinToString(", ")

            val prompt = """
                Kamu adalah sistem AI penganalisis bukti transaksi (struk fisik, invoice, resi, atau screenshot transaksi digital dari e-commerce/e-wallet seperti Shopee, Tokopedia, DANA, GoPay, dll).
                Tugas utama Anda adalah menganalisis gambar yang diberikan dan mengekstrak rincian transaksi (pengeluaran atau pendapatan).
                
                Langkah-langkah pemrosesan:
                1. Tentukan nama Toko/Merchant/Tempat transaksi/Aplikasi yang tertera pada bukti tersebut. Gunakan huruf kapital di awal setiap kata (misal: "Indomaret", "Shopee", "Tokopedia", "DANA", "Google Play").
                2. Cari tanggal transaksi dan format menjadi "YYYY-MM-DD". Jika tidak ada, kembalikan null atau kosong.
                3. Ekstrak daftar seluruh barang (produk) atau rincian transaksi beserta harga/jumlah masing-masing (harga akhir per barang setelah potong diskon jika ada). TIDAK PERLU menghitung total dari keseluruhan transaksi.
                4. Klasifikasikan / kategorikan setiap entri tersebut. (SANGAT PENTING) Utamakan memetakan ke daftar kategori saat ini jika cocok:
                   [$categoriesStr]
                   HANYA buat kategori baru jika benar-benar tidak ada yang pas (SANGAT DIHINDARI). Jika membuat baru, gunakan huruf awal kapital dan maksimal 2 kata. JANGAN menambahkan emoji pada nama kategori. Biarkan emoji masuk ke field 'emoji' saja.
                5. Tentukan satu karakter emoji yang paling merepresentasikan kategori barang tersebut (Contoh: "🍔" untuk Makanan/Minuman, "💄" untuk Kecantikan/Kosmetik, "🚗" untuk Transportasi, "🧾" untuk Tagihan, "🛒" untuk Belanja, "💊" untuk Kesehatan, "🐱" untuk Peliharaan/Hewan, "🏠" untuk Rumah Tangga, "👕" untuk Pakaian/Fashion, "🎮" untuk Game/Aplikasi, "📦" untuk Lainnya).
                6. Tentukan tipe dari entri tersebut: "expense" (pengeluaran) atau "income" (pemasukan/pendapatan, contohnya Refund, Cashback, Topup saldo yang masuk).
                
                Umpan balik HARUS berupa JSON murni dengan format seperti ini:
                {
                  "title": "Nama Toko/Merchant/Aplikasi",
                  "date": "2023-11-25",
                  "items": [
                    { "name": "Indomie Goreng", "amount": 15000.0, "category": "Makanan", "emoji": "🍔", "type": "expense" },
                    { "name": "Topup Saldo", "amount": 50000.0, "category": "Topup", "emoji": "💳", "type": "expense" },
                    { "name": "Refund Barang", "amount": 3330.0, "category": "Refund", "emoji": "🔄", "type": "income" }
                  ]
                }
                
                Jika gambar yang diberikan jelas-jelas sama sekali bukan berupa bukti transaksi (baik fisik maupun digital) atau tidak dapat dibaca sama sekali, kembalikan JSON seperti ini:
                {
                  "error": "bukan_struk"
                }
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    responseMimeType = "application/json"
                )
            )

            val modelName = "gemini-3.1-flash-lite"
            val url = GeminiClient.getFullUrl("v1beta/models/$modelName:generateContent")
            
            val response = kotlinx.coroutines.withTimeout(30_000L) {
                GeminiClient.service.generateContent(url, apiKey, request)
            }
            val rawResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (rawResponseText.isNullOrEmpty()) {
                return@withContext RequestResult.Error("Model AI tidak memberikan respon kosong.")
            }

            val cleanedJson = cleanJsonText(rawResponseText)
            val json = JSONObject(cleanedJson)

            val error = json.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
            if (error != null) {
                return@withContext RequestResult.Success(
                    ParsedReceipt(
                        title = "",
                        items = emptyList(),
                        grouped = emptyList(),
                        total = 0.0,
                        dateMillis = System.currentTimeMillis(),
                        error = error
                    )
                )
            }

            val title = json.optString("title", "Struk Belanja").trim()
            val dateString = json.optString("date", "").trim()
            
            var dateMillis = System.currentTimeMillis()
            if (dateString.isNotEmpty()) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val parsedDate = sdf.parse(dateString)
                    if (parsedDate != null) {
                        dateMillis = parsedDate.time
                    }
                } catch (e: Exception) {
                    // Ignore parsing error, fallback to current time
                }
            }
            
            val itemsList = mutableListOf<ReceiptItem>()
            val itemsArray = json.optJSONArray("items")
            var calculatedTotal = 0.0
            
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val amount = itemObj.optDouble("amount", 0.0)
                    val type = itemObj.optString("type", "expense").trim().lowercase().let { if (it == "income" || it == "pemasukan") "income" else "expense" }
                    
                    if (type == "income") {
                        calculatedTotal -= amount
                    } else {
                        calculatedTotal += amount
                    }
                    
                    itemsList.add(
                        ReceiptItem(
                            name = itemObj.optString("name", "Item").trim(),
                            amount = amount,
                            category = itemObj.optString("category", "Lainnya").trim(),
                            emoji = itemObj.optString("emoji", "📦").trim(),
                            type = type
                        )
                    )
                }
            }

            val groupedMap = mutableMapOf<String, MutableList<ReceiptItem>>()
            for (item in itemsList) {
                val groupKey = "${item.category}|${item.type}"
                groupedMap.getOrPut(groupKey) { mutableListOf() }.add(item)
            }

            val groupedList = groupedMap.entries.map { (catType, items) ->
                val (cat, type) = catType.split("|", limit = 2)
                val groupTotal = items.sumOf { it.amount }
                val itemNames = items.map { it.name }
                val desc = itemNames.take(3).joinToString(", ") + 
                           if (itemNames.size > 3) ", dll" else ""
                val groupEmoji = items.firstOrNull { it.emoji.isNotBlank() }?.emoji ?: "📦"
                GroupedReceiptTransaction(
                    category = cat,
                    description = desc,
                    amount = groupTotal,
                    emoji = groupEmoji,
                    type = type
                )
            }.toMutableList()

            RequestResult.Success(
                ParsedReceipt(
                    title = title,
                    items = itemsList,
                    grouped = groupedList,
                    total = calculatedTotal,
                    dateMillis = dateMillis
                )
            )
        } catch (e: Exception) {
            RequestResult.Error("Gagal menganalisis struk: ${e.localizedMessage ?: "kesalahan tidak dikenal"}", e)
        }
    }

    private fun cleanJsonText(raw: String): String {
        var text = raw.trim()
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1)
        }
        return text
    }
}
