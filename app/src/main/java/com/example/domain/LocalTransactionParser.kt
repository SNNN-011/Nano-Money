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

        /** Kata yang boleh menemani jawaban singkat atas pertanyaan aplikasi. */
        private val CLARIFICATION_WORDS = setOf(
            "uang", "itu", "tadi", "yang", "ya", "yah", "iya", "sih", "aja", "saja",
            "nih", "dong", "kok", "kan", "sebesar", "adalah", "nominal", "nominalnya",
            "masuk", "keluar", "pemasukan", "pengeluaran", "income", "expense", "in", "out"
        )

        private val INCOME_WORDS = setOf("masuk", "pemasukan", "income", "in")
        private val EXPENSE_WORDS = setOf("keluar", "pengeluaran", "expense", "out")

        /** Kata satuan uang yang boleh muncul di jawaban nominal murni. */
        private val AMOUNT_WORDS_PATTERN =
            Regex("(?i)\\b(rp|idr|rupiah|rb|ribu|ribuan|k|jt|jta|juta|jutaan|m|miliar|milyar|perak)\\b")

        /**
         * Kata yang tidak menambah arti apa pun kalau berdiri sendiri di samping nominal.
         *
         * "uang 200 ribu" dan "ada 1 juta nih" cuma menyebut nominal: arah uangnya
         * tidak ada di kalimat itu, jadi aplikasi harus bertanya -- bukan mencatat
         * tebakan model sebagai pemasukan.
         */
        private val AMOUNT_FILLER_WORDS = setOf(
            "uang", "ada", "nih", "sih", "dong", "aja", "saja", "ya", "yah", "iya",
            "itu", "tadi", "yang", "kok", "kan", "doang", "bgt", "banget", "nominal",
            "nominalnya", "sebesar", "adalah", "rp", "idr", "rupiah", "perak"
        )

        /**
         * Kata yang tidak layak jadi nama item kalau berdiri sendiri.
         *
         * Model kadang mengisi `item` dengan kata kerja atau kata arah ("Beli",
         * "Masuk"), dan itu tampil di kartu konfirmasi sebagai nama transaksi.
         * Kalau seluruh isi item seperti ini, item dibuang dan deskripsi disusun
         * dari kalimat user.
         */
        private val ITEM_FILLER_WORDS = CLARIFICATION_WORDS + setOf(
            "beli", "bayar", "bayarin", "beliin", "isi", "topup", "sewa", "jajan",
            "belanja", "terima", "dapat", "dapet", "jual", "kirim", "transfer",
            "cicil", "angsuran", "ongkos", "parkir", "tarif", "bayarnya", "belinya"
        )
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

        // Jawaban singkat atas pertanyaan aplikasi sendiri ("masuk", "50rb") tidak
        // perlu dikirim ke model: pertanyaannya cuma dua (nominal / arah), jadi
        // isinya bisa disusun dari kalimat sebelumnya. Tanpa jalur ini model kadang
        // tetap tidak memanggil alat, lalu aplikasi menanyakan hal yang sama sampai
        // batas 3 kali lalu menyerah.
        quickAnswer(inputText, pendingContext, incomeCategories, expenseCategories)
            ?.let { return RequestResult.Success(it) }

        // Kalimat yang isinya cuma nominal ("uang 200 ribu", "ada 1 juta nih")
        // tidak menyebut arah uangnya sama sekali. Model di sini cuma menebak,
        // dan tebakan yang tersimpan sebagai pemasukan lebih merugikan daripada
        // satu pertanyaan singkat.
        leftoverAfterAmount(inputText)?.let { sisa ->
            if (sisa.isEmpty()) {
                return RequestResult.Success(
                    ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_TYPE)
                )
            }
            directionWord(sisa)?.let { arah ->
                return RequestResult.Success(
                    clarificationResult(
                        AmountReader.fromText(inputText), arah, inputText,
                        incomeCategories, expenseCategories
                    )
                )
            }
        }

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
                readableAmount(query) != null -> {
                    // "gajian 5jt": arahnya jelas dari kata kerjanya, jadi tidak
                    // perlu ditanya lagi -- cukup susun dari kalimat user.
                    val arah = TransactionCorrector.directionFromVerbs(query)
                    if (arah != null) RequestResult.Success(
                        clarificationResult(
                            readableAmount(query), arah, query,
                            incomeCategories, expenseCategories
                        )
                    ) else RequestResult.Success(
                        ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_TYPE)
                    )
                }
                hasSuppressedCall(envelope) -> RequestResult.Success(
                    ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_AMOUNT)
                )
                // Ada kata kerja transaksi ("isi bensin") tapi nominalnya belum
                // disebut. Dulu ini dijawab "bukan transaksi" lalu catatannya
                // hilang; yang kurang nominal, bukan transaksinya.
                TransactionCorrector.looksLikeTransaction(query) -> RequestResult.Success(
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
        val item = cleanItem(args.optString("item", ""))
        val validType =
            if (corrected == TransactionCorrector.TYPE_INCOME) "PEMASUKAN" else "PENGELUARAN"

        // Kategori model diperbaiki dengan kata kunci dari data latih ("bensin" ->
        // Transportasi), tapi hanya kalau kategorinya benar-benar ada di daftar
        // user untuk jenis yang sudah dikoreksi. Kalau tidak ada yang cocok,
        // jawaban model dibiarkan (dan nanti jatuh ke "Lainnya" lewat resolver).
        val candidates =
            if (corrected == TransactionCorrector.TYPE_INCOME) incomeCategories else expenseCategories
        // Kategori model hanya dipakai kalau tidak berasal dari daftar arah lawan:
        // "Makanan" tidak boleh menempel di transaksi pemasukan.
        val aiCategoryValid = if (corrected == aiType) {
            aiCategory.ifBlank { null }
        } else {
            aiCategory.takeIf {
                CategoryResolver.resolve(it, candidates) is CategoryResolution.Matched
            }
        }
        val finalCategory = CategoryHints.suggest(item, candidates)
            ?: CategoryHints.suggest(inputText, candidates)
            ?: aiCategoryValid

        return RequestResult.Success(
            ExtractionResult(
                // Deskripsi kosong adalah keluhan nyata di HP: `item` sering tidak
                // diisi model. Kalimat user (tanpa nominal/tanggal) jauh lebih
                // berguna daripada kolom kosong.
                description = item.ifBlank { null }?.capitalizeFirst() ?: describeFrom(query),
                amount = amount,
                type = validType,
                category = finalCategory,
                date = clock(),
                notes = null
            )
        )
    }

    /**
     * Jawaban singkat atas pertanyaan aplikasi sendiri, disusun tanpa model.
     *
     * Hanya dua pertanyaan yang pernah diajukan aplikasi: nominal dan arah uang.
     * Jadi kalau user menjawab "masuk" / "50rb", maksudnya sudah pasti dan tidak
     * perlu ditebak model yang mungkin kembali tidak memanggil alat.
     */
    private fun quickAnswer(
        input: String,
        pending: String?,
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): ExtractionResult? {
        if (pending.isNullOrBlank()) return null

        val tokens = CategoryResolver.normalize(input).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val arah = directionWord(tokens)

        // "masuk", "uang keluar", "iya keluar" -> nominal diambil dari kalimat awal.
        if (arah != null && tokens.all { it in CLARIFICATION_WORDS }) {
            val amount = AmountReader.fromText(pending) ?: AmountReader.fromText(input)
            return clarificationResult(amount, arah, pending, incomeCategories, expenseCategories)
        }

        // "50rb", "Rp 50.000" -> arah dibaca dari kata kerja kalimat awal.
        if (arah == null && isPlainAmount(input)) {
            val arahLama = TransactionCorrector.directionFromVerbs(pending)
                ?: return ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_TYPE)
            return clarificationResult(
                AmountReader.fromText(input), arahLama, pending, incomeCategories, expenseCategories
            )
        }

        return null
    }

    /** Hasil transaksi dari jawaban klarifikasi; nominal kosong -> tanya nominal. */
    private fun clarificationResult(
        amount: Long?,
        type: String,
        source: String,
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): ExtractionResult {
        val nominal = amount?.takeIf { it in 1..AI_MAX_AMOUNT }
            ?: return ExtractionResult(error = "klarifikasi", pertanyaan = QUESTION_AMOUNT)

        val income = type == TransactionCorrector.TYPE_INCOME
        val candidates = if (income) incomeCategories else expenseCategories

        return ExtractionResult(
            description = describeFrom(source),
            amount = nominal,
            type = if (income) "PEMASUKAN" else "PENGELUARAN",
            category = CategoryHints.suggest(source, candidates),
            date = clock(),
            notes = null
        )
    }

    /** Arah uang dari kata yang dipakai user menjawab pertanyaan aplikasi. */
    private fun directionWord(tokens: List<String>): String? {
        val masuk = tokens.any { it in INCOME_WORDS }
        val keluar = tokens.any { it in EXPENSE_WORDS }
        return when {
            masuk && !keluar -> TransactionCorrector.TYPE_INCOME
            keluar && !masuk -> TransactionCorrector.TYPE_EXPENSE
            else -> null
        }
    }

    /**
     * Benar kalau [input] hanya berisi nominal ("50rb", "Rp 50.000", "50 ribu").
     *
     * Selain angka, hanya kata satuan uang yang boleh muncul; satu kata lain
     * ("masuk 50rb") berarti kalimatnya bukan jawaban nominal murni.
     */
    private fun isPlainAmount(input: String): Boolean {
        if (AmountReader.fromText(input) == null) return false
        val sisa = input
            .replace(Regex("[0-9.,]+"), " ")
            .replace(AMOUNT_WORDS_PATTERN, " ")
            .replace(Regex("[^\\p{L}]"), " ")
            .trim()
        return sisa.isEmpty()
    }

    /**
     * Sisa kata setelah nominal dibuang, atau null kalau kalimatnya tidak menyebut nominal.
     *
     * "uang 200 ribu" -> [], "200 ribu masuk" -> ["masuk"], "makan siang 25 ribu"
     * -> ["makan", "siang"]. Kata pengisi ("uang", "nih") dibuang karena tidak
     * memberi arah apa pun.
     */
    private fun leftoverAfterAmount(input: String): List<String>? {
        if (AmountReader.fromText(input) == null) return null
        return CategoryResolver.normalize(input)
            .replace(Regex("[0-9.,]+"), " ")
            .replace(AMOUNT_WORDS_PATTERN, " ")
            .replace(Regex("[^\\p{L}]"), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in AMOUNT_FILLER_WORDS }
    }

    /**
     * Nama item dari model, dibuang kalau isinya cuma kata kerja/kata arah.
     *
     * "Beli" atau "Masuk" bukan nama transaksi; dengan mengosongkannya, deskripsi
     * jatuh ke [describeFrom] yang memakai kalimat asli user.
     */
    private fun cleanItem(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val kata = CategoryResolver.normalize(text).split(' ').filter { it.isNotBlank() }
        return if (kata.none { it !in ITEM_FILLER_WORDS }) "" else text
    }

    /**
     * Deskripsi cadangan dari kalimat user: nominal dan tanggal dibuang.
     *
     * "makan siang 25 ribu" -> "Makan siang". Kalau setelah dibersihkan tidak
     * tersisa apa pun ("2500000"), hasilnya null dan pemanggil memakai teksnya
     * sendiri.
     */
    private fun describeFrom(text: String): String? {
        val bersih = text
            .replace(Regex("(?i)\\b(rp|idr)\\.?"), " ")
            .replace(Regex("\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b"), " ")
            .replace(Regex("(?i)\\b\\d[\\d.,]*\\s*(rb|ribu|ribuan|k|jt|jta|juta|jutaan|m|miliar|milyar)?"), " ")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (bersih.isBlank()) return null
        return bersih.split(' ').take(6).joinToString(" ").take(60).capitalizeFirst()
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
