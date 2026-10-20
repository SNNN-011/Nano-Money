package com.example.domain

import com.example.data.local.LocalEngine
import com.example.data.remote.ExtractionResult
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject

/**
 * Parser transaksi memakai model lokal (hasil training, jalan tanpa internet).
 *
 * Modelnya dilatih untuk mengeluarkan *function call* (`catat_pengeluaran`,
 * `catat_masuk`, `tanya`), bukan JSON bebas seperti prompt Gemini. Terjemahannya
 * ke [ExtractionResult] dikumpulkan di sini supaya sisa aplikasi -- dialok
 * konfirmasi, penyaring kategori, penyimpanan -- tidak perlu tahu otak mana yang
 * sedang dipakai.
 *
 * Dua hal yang sengaja TIDAK diambil dari model:
 * - tanggal: selalu [clock] (hari ini). Tanggal ditanyakan aplikasi, bukan model.
 * - deskripsi/notes/emoji: nama item dipakai sebagai deskripsi, sisanya diisi
 *   lapisan aplikasi yang sudah ada.
 *
 * Satu lagi yang tidak dipercaya mentah: nominal. Kalau user menulis nominalnya
 * dengan jelas ("Rp 25.000", "2 juta"), angka itulah yang dipakai; lihat
 * [AmountReader]. Angka model hanya dipakai kalau kalimatnya tidak menyebut
 * nominal secara eksplisit.
 */
class LocalTransactionParser(
    private val engine: LocalEngine,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private companion object {
        /** Panjang maksimum teks user yang dikirim ke model. */
        const val MAX_USER_TEXT_CHARS = 500

        /** Panjang maksimum konteks klarifikasi yang digabung ke teks baru. */
        const val MAX_CONTEXT_CHARS = 500

        /** Sama dengan setelan eval (vm/eval_app.py). */
        const val MAX_NEW_TOKENS = 128

        const val TOOL_PENGELUARAN = "catat_pengeluaran"
        const val TOOL_MASUK = "catat_masuk"
        const val TOOL_TANYA = "tanya"

        const val QUESTION_AMOUNT = "Berapa nominalnya?"
        const val QUESTION_TYPE = "Itu uang masuk atau uang keluar?"
        const val QUESTION_OTHER = "Bisa tolong lengkapi keterangan transaksinya?"
    }

    suspend fun parseTransaction(
        userText: String,
        pendingContext: String?,
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): RequestResult<ExtractionResult> {
        val inputText = sanitize(userText)
        if (inputText.isBlank()) {
            return RequestResult.Error("Teks transaksi kosong", null)
        }

        // Model dilatih satu kalimat per permintaan (tanpa percakapan), jadi
        // jawaban user atas pertanyaan klarifikasi digabung dengan kalimat
        // sebelumnya supaya terbaca sebagai satu kalimat utuh.
        val query = pendingContext
            ?.take(MAX_CONTEXT_CHARS)
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it $inputText" }
            ?: inputText

        // Daftar kategori user diteruskan ke model dulu: otak v5 memilih
        // kategori dari daftar yang DIBERIKAN. Tanpa ini kategori benar
        // anjlok ke 0,7-4% (lihat vm/CATATAN.md, mode eval `app`).
        engine.setCategories(incomeCategories, expenseCategories)

        val raw = try {
            engine.complete(query, MAX_NEW_TOKENS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RequestResult.Error("Otak lokal gagal menjawab. Coba lagi.", e)
        }

        val envelope = try {
            JSONObject(trimToObject(raw))
        } catch (e: JSONException) {
            return RequestResult.Error("Jawaban otak lokal tidak sesuai format.", e)
        }

        val call = firstCall(envelope)
        if (call == null) {
            // Tidak ada panggilan yang tepercaya. Dua hal masih bisa dibedakan:
            // kalimat user sendiri sudah menyebut nominalnya (tinggal tanya arah
            // uangnya), atau mesin menahan panggilan berkeyakinan rendah (tanya
            // nominalnya). Isi panggilan yang ditahan tidak pernah dipakai untuk
            // menyimpan apa pun.
            return when {
                readableAmount(query) != null -> RequestResult.Success(
                    ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_TYPE)
                )
                hasSuppressedCall(envelope) -> RequestResult.Success(
                    ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_AMOUNT)
                )
                else -> RequestResult.Success(ExtractionResult(error = "bukan_transaksi"))
            }
        }

        val name = call.optString("name", "").trim()
        val args = call.optJSONObject("arguments") ?: JSONObject()

        if (name == TOOL_TANYA) {
            return RequestResult.Success(
                ExtractionResult(error = "klarifikasi", pertanyaan = questionFor(args.optString("field", "")))
            )
        }

        if (name != TOOL_PENGELUARAN && name != TOOL_MASUK) {
            return RequestResult.Success(ExtractionResult(error = "bukan_transaksi"))
        }

        // Nominal dari kalimat user dipakai lebih dulu daripada angka model.
        // Model ini salah baca satuan besar ("2 juta" dijawab 2000), sedangkan
        // kalimat user menyebut nominalnya dengan jelas. Kalau user tidak
        // menulis nominal yang jelas, jawaban model yang dipakai.
        val amount = AmountReader.fromText(query) ?: args.optAmountLong("amount", 0L)
        if (amount <= 0L || amount > AI_MAX_AMOUNT) {
            // Nominal tidak terbaca: tanyakan, jangan simpan angka karangan.
            return RequestResult.Success(
                ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_AMOUNT)
            )
        }

        val aiCategory = args.optString("category", "").trim()
        val aiType = if (name == TOOL_MASUK) TransactionCorrector.TYPE_INCOME else TransactionCorrector.TYPE_EXPENSE
        val corrected = TransactionCorrector.correctType(
            userText = inputText,
            aiType = aiType,
            aiCategory = aiCategory,
            incomeCategories = incomeCategories,
            expenseCategories = expenseCategories
        )
        val validType =
            if (corrected == TransactionCorrector.TYPE_INCOME) "PEMASUKAN" else "PENGELUARAN"

        return RequestResult.Success(
            ExtractionResult(
                description = args.optString("item", "").trim().ifBlank { null }?.capitalizeFirst(),
                amount = amount,
                type = validType,
                category = aiCategory.ifBlank { null },
                date = clock(),
                notes = null
            )
        )
    }

    /**
     * Membaca function call pertama dari balasan mesin.
     *
     * Balasan berbentuk {"function_calls":[{"name":..,"arguments":{..}}], ...};
     * daftar kosong berarti model tidak menemukan transaksi di teks itu.
     */
    private fun firstCall(envelope: JSONObject): JSONObject? {
        val calls = envelope.optJSONArray("function_calls") ?: return null
        if (calls.length() == 0) return null
        return calls.optJSONObject(0)
    }

    /**
     * Benar kalau mesin menahan sekurang-kurangnya satu panggilan.
     *
     * Panggilan yang ditahan berarti keyakinannya di bawah ambang, jadi isinya
     * tidak layak dipakai. Yang dibaca di sini cuma kehadirannya, sebagai alasan
     * untuk bertanya.
     */
    private fun hasSuppressedCall(envelope: JSONObject): Boolean {
        val calls = envelope.optJSONArray("suppressed_calls") ?: return false
        return calls.length() > 0
    }

    /** Nominal yang jelas terbaca dari kalimat user, dalam rentang yang wajar. */
    private fun readableAmount(text: String): Long? =
        AmountReader.fromText(text)?.takeIf { it in 1..AI_MAX_AMOUNT }

    /** Membuang teks di luar {...} supaya satu baris basa-basi tidak merusak parsing. */
    private fun trimToObject(raw: String): String {
        val text = raw.trim()
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        return if (first != -1 && last > first) text.substring(first, last + 1) else text
    }

    private fun questionFor(field: String): String = when (field.trim().lowercase()) {
        "amount", "nominal" -> QUESTION_AMOUNT
        "type", "jenis" -> QUESTION_TYPE
        else -> QUESTION_OTHER
    }

    private fun sanitize(raw: String): String = raw
        .take(MAX_USER_TEXT_CHARS)
        .replace(Regex("[\\u0000-\\u001F]"), " ")
        .trim()

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
