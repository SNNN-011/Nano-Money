package com.example.domain

import com.example.ui.ExtractionResult
import com.example.ui.GeminiClient
import com.example.ui.GeminiContent
import com.example.ui.GeminiGenerationConfig
import com.example.ui.GeminiPart
import com.example.ui.GeminiRequest
import com.example.ui.GeminiSchemaOptions
import com.example.ui.GeminiSchemaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class RequestResult<out T> {
    data class Success<out T>(val data: T) : RequestResult<T>()
    data class Error(val message: String, val e: Throwable? = null) : RequestResult<Nothing>()
}

class TransactionParserUseCase {

    private fun getSystemPrompt(
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): String {
        val nowMs = System.currentTimeMillis()
        val zoneId = java.time.ZoneId.systemDefault()
        val localDate = try {
            java.time.Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        } catch (e: Exception) {
            java.time.LocalDate.now()
        }
        val currentYear = localDate.year
        val currentMonth = localDate.monthValue

        val tanggalBulanIni = (1..localDate.lengthOfMonth()).map { day ->
            val date = java.time.LocalDate.of(currentYear, currentMonth, day)
            val tsMs = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
            "  tanggal $day = $tsMs"
        }.joinToString("\n")

        val kemarin = localDate.minusDays(1).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val kemarinLusa = localDate.minusDays(2).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val mingguLalu = localDate.minusDays(7).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

        val dayOfWeekIndonesian = when (localDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY    -> "Senin"
            java.time.DayOfWeek.TUESDAY   -> "Selasa"
            java.time.DayOfWeek.WEDNESDAY -> "Rabu"
            java.time.DayOfWeek.THURSDAY  -> "Kamis"
            java.time.DayOfWeek.FRIDAY    -> "Jumat"
            java.time.DayOfWeek.SATURDAY  -> "Sabtu"
            java.time.DayOfWeek.SUNDAY    -> "Minggu"
            else -> ""
        }
        val monthName = when (localDate.monthValue) {
            1 -> "Januari" 2 -> "Februari" 3 -> "Maret" 4 -> "April" 5 -> "Mei" 6 -> "Juni"
            7 -> "Juli" 8 -> "Agustus" 9 -> "September" 10 -> "Oktober" 11 -> "November" 12 -> "Desember"
            else -> ""
        }
        val formattedDate = "$dayOfWeekIndonesian, ${localDate.dayOfMonth} $monthName ${localDate.year}"

        val incomeCats = incomeCategories.map { com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second }.filter { it.isNotBlank() }.distinct().joinToString("|")
        val expenseCats = expenseCategories.map { com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second }.filter { it.isNotBlank() }.distinct().joinToString("|")

        return """
            Kamu adalah parser transaksi keuangan. Ekstrak data dari teks Bahasa Indonesia dan kembalikan HANYA JSON.
            
            ## KONTEKS WAKTU
            Sekarang: $formattedDate
            Timestamp sekarang: $nowMs
            Konstanta: 1hari=86400000ms | 1jam=3600000ms | 1minggu=604800000ms | 1bulan=2592000000ms
            
            ## CARA HITUNG TANGGAL
            PENTING: Gunakan tabel referensi di bawah. JANGAN hitung sendiri — langsung ambil nilai timestamp dari tabel.

            ### Referensi tanggal bulan ini ($monthName $currentYear):
            $tanggalBulanIni

            ### Referensi lainnya:
            hari ini = $nowMs
            kemarin = $kemarin
            kemarin lusa = $kemarinLusa
            minggu lalu = $mingguLalu

            ### Aturan:
            - "tanggal X" atau "tgl X" → ambil nilai dari tabel bulan ini di atas
            - "tanggal X [NamaBulan]" → gunakan nilai bulan itu jika tersedia di tabel, atau estimasi terbaik
            - "hari ini", "tadi", "barusan", "pagi/siang/sore/malam ini" → gunakan $nowMs
            - "kemarin" → gunakan $kemarin
            - "kemarin lusa" → gunakan $kemarinLusa  
            - "minggu lalu" → gunakan $mingguLalu
            - "X hari yang lalu" → kurangi (X × 86400000) dari $nowMs
            - Tidak ada keterangan waktu → gunakan $nowMs    
                    
            ## CARA HITUNG AMOUNT
            - k/rb/ribu = ×1.000 | jt/juta = ×1.000.000
            
            ## OUTPUT FORMAT
            Keluarkan JSON.
            "error" isi "bukan_transaksi" kalau tidak relevan.
            "error" isi "klarifikasi" dan isi "pertanyaan" bila info belum lengkap.
            "description" deskripsi pendek transaksi(maksimal 2 kata), gunakan huruf kapital di setiap awal kalimat.
            "amount" nominal.
            "type" "income" atau "expense".
            "category" SANGAT PENTING: Pilih SATU dari opsi berikut yang PALING COCOK. Untuk income: [$incomeCats]. Untuk expense: [$expenseCats]. JANGAN buat kategori baru KECUALI benar-benar tidak ada yang pas (gunakan opsi yang ada sebisa mungkin).
            "date" timestamp_ms.
            "notes" rangkuman singkat transaksi(maksimal 4 kata).
            "emoji" satu karakter emoji yang merepresentasikan transaksi/kategori ini (Contoh: "🍔" untuk makan/minum, "🚗" untuk bensin/ojek, "🛒" untuk belanja, "💊" untuk obat, "💵" atau "💰" untuk gaji/bonus, "🔌" untuk tagihan/listrik, dll).
        """.trimIndent()
    }

    suspend fun parseTransaction(
        apiKey: String,
        modelName: String,
        userText: String,
        pendingContext: String?,
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): RequestResult<ExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val inputText = userText.replace(Regex("(?i)(abaikan|instruksi|system|prompt|bypass|override)"), "[REDACTED]")

            val contents = mutableListOf<GeminiContent>()
            if (pendingContext != null) {
                contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = "Konteks sebelumnya:\n$pendingContext\n\nInput baru:\n$inputText"))))
            } else {
                contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = inputText))))
            }

            val schemaProperties = mapOf(
                "description" to GeminiSchemaItem(type = "STRING", description = "nama item singkat", nullable = true),
                "amount" to GeminiSchemaItem(type = "NUMBER", nullable = true),
                "type" to GeminiSchemaItem(type = "STRING", description = "income atau expense", nullable = true),
                "category" to GeminiSchemaItem(type = "STRING", nullable = true),
                "date" to GeminiSchemaItem(type = "NUMBER", description = "timestamp in milliseconds", nullable = true),
                "notes" to GeminiSchemaItem(type = "STRING", nullable = true),
                "emoji" to GeminiSchemaItem(type = "STRING", description = "Satu karakter emoji yang paling merepresentasikan transaksi ini", nullable = true),
                "error" to GeminiSchemaItem(type = "STRING", description = "If not a transaction, set to 'bukan_transaksi'. If clarification needed, set to 'klarifikasi'.", nullable = true),
                "pertanyaan" to GeminiSchemaItem(type = "STRING", nullable = true)
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = getSystemPrompt(incomeCategories, expenseCategories)))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    responseMimeType = "application/json",
                    responseSchema = GeminiSchemaOptions(type = "OBJECT", properties = schemaProperties)
                )
            )

            var rawResponseText: String? = null
            var lastException: Throwable? = null

            val modelsToTry = listOf(modelName, "gemini-2.0-flash-lite", "gemini-1.5-flash")

            for (model in modelsToTry) {
                try {
                    val url = "https://nano-money.yasinhacker135.workers.dev/v1beta/models/$model:generateContent"
                    val response = kotlinx.coroutines.withTimeout(30_000L) {
                        GeminiClient.service.generateContent(url, apiKey, request)
                    }
                    rawResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!rawResponseText.isNullOrEmpty()) break
                } catch (e: Throwable) {
                    lastException = e
                }
            }

            if (rawResponseText.isNullOrEmpty()) {
                return@withContext RequestResult.Error("API tidak merespons", lastException)
            }

            val cleanedJson = cleanJsonText(rawResponseText)
            val json = JSONObject(cleanedJson)

            val error = json.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
            if (error != null) {
                val p = json.optString("pertanyaan", "").takeIf { it.isNotBlank() && it != "null" }
                return@withContext RequestResult.Success(ExtractionResult(error = error, pertanyaan = p))
            }

            val result = ExtractionResult(
                description = json.optString("description", "Transaksi"),
                amount = json.optDouble("amount", 0.0),
                type = json.optString("type", "PENGELUARAN"),
                category = json.optString("category", "Lainnya"),
                date = json.optLong("date", System.currentTimeMillis()),
                notes = json.optString("notes", ""),
                emoji = json.optString("emoji", "📦").trim()
            )

            RequestResult.Success(result)
        } catch (e: Exception) {
            RequestResult.Error(e.message ?: "Kesalahan pemrosesan data", e)
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
