package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.CategoryRepository
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

import android.graphics.Bitmap
import com.example.domain.ParsedReceipt
import com.example.data.remote.ExtractionResult
import com.example.ui.config.AiModelConfig
import com.example.ui.config.FormatUtils

sealed class ChatMessage {
    data class UserMessage(val text: String) : ChatMessage()
    data class UserImageMessage(val bitmap: Bitmap) : ChatMessage()
    data class AiMessage(
        val text: String,
        val record: FinancialRecord? = null,
        /** True kalau pesan ini pemberitahuan kegagalan, bukan jawaban AI. */
        val isError: Boolean = false,
        /** Teks user yang bisa dikirim ulang lewat tombol "Coba Lagi". */
        val retryText: String? = null
    ) : ChatMessage()
    object TypingIndicator : ChatMessage()
    object ScanningReceiptIndicator : ChatMessage()
}

/**
 * Otak yang dipakai chat: model lokal (hasil training, tanpa internet) atau
 * Gemini (online, termasuk foto struk).
 *
 * Bawaannya Gemini supaya perilaku aplikasi tidak berubah sampai user memilih
 * sendiri mode lokal di layar chat.
 */
enum class AiMode(val prefValue: String) {
    LOCAL("local"),
    GEMINI("gemini");

    companion object {
        fun fromPref(raw: String?): AiMode =
            values().firstOrNull { it.prefValue == raw } ?: GEMINI
    }
}

class ChatViewModel(
    private val application: Application,
    private val repository: FinancialRecordRepository
) : ViewModel() {

    private val prefs = com.example.util.SecurePrefsHelper.getEncryptedPrefs(application, "financial_tracker_prefs")

    /**
     * Kategori dibaca dari tabel Room, bukan dari string CSV di prefs.
     *
     * Sebelumnya tiap layar menyimpan daftarnya sendiri-sendiri di prefs, sehingga
     * kategori yang ditambah lewat chat tidak terkirim ke cloud dan tidak muncul
     * di perangkat lain. Sekarang semuanya lewat [CategoryRepository].
     */
    private val categoryRepository = CategoryRepository(
        AppDatabase.getDatabase(application).categoryDao(),
        prefs
    )

    /**
     * Daftar kategori yang dikirim ke AI dan dipakai untuk pencocokan.
     *
     * WAJIB [SharingStarted.Eagerly], bukan WhileSubscribed. Aliran ini dibaca
     * lewat `.value` dari beberapa tempat yang bukan composable -- saat membangun
     * prompt dan saat menyimpan transaksi. Dengan WhileSubscribed,
     * aliran hanya hidup selama ada collector, dan collector-nya yang ada
     * (dialog konfirmasi) hanya tersusun ketika dialog itu terbuka. Akibatnya
     * saat chat biasa, `.value` selalu kosong: prompt terkirim dengan
     * "Daftar expense: []" dan AI tidak pernah melihat kategori milik user.
     *
     * Jangan dikembalikan ke WhileSubscribed tanpa menaikkan juga semua tempat
     * yang membacanya ke collector.
     */
    val incomeCategories: StateFlow<List<String>> = categoryRepository.observeByType("income")
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val expenseCategories: StateFlow<List<String>> = categoryRepository.observeByType("expense")
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage.AiMessage("Halo! Ceritakan transaksimu, contoh: 'beli eskrim 20rb' atau Foto struk belanja kamu😊")
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _pendingClarificationContext = MutableStateFlow<String?>(null)
    val pendingClarificationContext: StateFlow<String?> = _pendingClarificationContext.asStateFlow()

    private val _clarificationAttempts = MutableStateFlow(0)
    val clarificationAttempts: StateFlow<Int> = _clarificationAttempts.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ExtractionResult?>(null)
    val pendingConfirmation: StateFlow<ExtractionResult?> = _pendingConfirmation.asStateFlow()

    /**
     * Otak yang sedang dipakai. Disimpan di prefs yang sama dengan setelan chat
     * lainnya supaya pilihan user bertahan setelah aplikasi ditutup.
     */
    private val _aiMode = MutableStateFlow(AiMode.fromPref(prefs.getString(PREF_AI_MODE, null)))
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    fun setAiMode(mode: AiMode) {
        if (_aiMode.value == mode) return
        _aiMode.value = mode
        prefs.edit().putString(PREF_AI_MODE, mode.prefValue).apply()
    }

    /**
     * Otak lokal dibuat malas: model 63 MB baru dibaca saat benar-benar dipakai,
     * bukan saat layar chat dibuka.
     */
    private val localParser by lazy {
        com.example.domain.LocalTransactionParser(
            com.example.data.local.NeedleLocalEngine(application)
        )
    }

    fun updatePendingTransaction(updated: ExtractionResult) {
        _pendingConfirmation.value = updated
    }

    fun confirmPendingTransaction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        
        // Add user-like message to maintain conversational flow history
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage("Ya, simpan"))
        _messages.value = currentList

        viewModelScope.launch {
            saveAndConfirmTransaction(pending, "ya")
        }
    }

    fun cancelPendingTransaction() {
        _pendingConfirmation.value = null

        // Konteks klarifikasi ikut dibuang: kalau tidak, pesan berikutnya masih
        // dibaca AI sebagai lanjutan percakapan lama yang sudah dibatalkan user.
        _pendingClarificationContext.value = null
        _clarificationAttempts.value = 0

        // Add user-like message to maintain conversational flow history
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage("Batal"))
        _messages.value = currentList

        addAiMessage("Oke, transaksi dibatalkan 👍")
    }

    private val _pendingReceiptConfirmation = MutableStateFlow<ParsedReceipt?>(null)
    val pendingReceiptConfirmation: StateFlow<ParsedReceipt?> = _pendingReceiptConfirmation.asStateFlow()

    fun updatePendingReceipt(updated: ParsedReceipt) {
        _pendingReceiptConfirmation.value = updated
    }

    private var lastRequestTime = 0L
    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    /** Job AI yang sedang berjalan; disimpan supaya bisa dibatalkan user. */
    private var aiJob: Job? = null

    /**
     * Membatalkan permintaan AI yang sedang berjalan (tombol "Batalkan" di layar chat).
     * Pembatalan menjalar lewat CancellationException sampai panggilan HTTP.
     */
    fun cancelAiProcessing() {
        if (!_isAiProcessing.value) return
        aiJob?.cancel()
        aiJob = null
        _isAiProcessing.value = false
        lastRequestTime = 0L
        removeTypingIndicator()
        addAiMessage("Permintaan AI dibatalkan.")
    }

    /**
     * Thumbnail untuk bubble chat; bitmap asli (sampai 2048 px) tidak boleh ikut
     * tersimpan.
     *
     * 768 px, bukan 160 px seperti sebelumnya: gelembung menampilkan gambar
     * sampai 250 dp, dan di layar berkerapatan 3x itu butuh sekitar 750 px.
     * Dengan 160 px gambar di-zoom lebih dari 4x sehingga terlihat pecah.
     * 768 px ARGB_8888 kira-kira 1,8 MB per foto -- jauh di bawah 12-16 MB
     * bitmap asli yang dulu membuat aplikasi kehabisan memori.
     */
    private fun createThumbnail(source: Bitmap): Bitmap {
        val maxDimension = 768
        val scale = minOf(
            maxDimension / source.width.toFloat(),
            maxDimension / source.height.toFloat(),
            1f
        )
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
    }

    private val receiptUseCase = com.example.domain.ReceiptParserUseCase()

    fun scanReceipt(bitmap: Bitmap) {
        // Foto struk dibaca model gambar (Gemini). Otak lokalnya teks saja, jadi
        // jalur ini ditolak di pangkal, bukan gagal di tengah jalan.
        if (_aiMode.value == AiMode.LOCAL) {
            addAiMessage("Mode AI Lokal belum bisa membaca foto struk. Ganti ke AI Gemini atau ketik transaksinya ya 🙂")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRequestTime < 600L || _isAiProcessing.value) return
        lastRequestTime = now
        _isAiProcessing.value = true

        // Bubble chat hanya menyimpan thumbnail: chatViewModel ber-scope Activity,
        // sedangkan bitmap struk ≤2048x2048 ARGB_8888 bisa 12–16 MB per foto dan
        // dulu menumpuk sampai OOM.
        val thumbnail = createThumbnail(bitmap)

        // Tampilkan scanning receipt indicator
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserImageMessage(thumbnail))
        _messages.value = currentList

        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.ScanningReceiptIndicator)
        _messages.value = listWithTyping

        aiJob = viewModelScope.launch {
            try {
                val apiKeyRaw = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" } ?: ""
                val apiKey = if (apiKeyRaw.isEmpty() || apiKeyRaw == "MY_GEMINI_API_KEY" || apiKeyRaw == "GEMINI_API_KEY") {
                    "CF_PROXY_KEY"
                } else {
                    apiKeyRaw
                }

                val resultStatus = receiptUseCase.parseReceipt(
                    apiKey = apiKey,
                    bitmap = bitmap,
                    expenseCategories = expenseCategories.value
                )

                removeTypingIndicator()

                when (resultStatus) {
                    is com.example.domain.RequestResult.Error -> {
                        val msg = if (resultStatus.message == "Sesi login bermasalah, silakan coba lagi") {
                            resultStatus.message
                        } else {
                            "Gagal memproses struk: ${resultStatus.message}"
                        }
                        // Tanpa retryText: mengulang butuh bitmap struk, dan bitmap
                        // sudah dilepas di blok finally di bawah.
                        addAiMessage(msg, isError = true)
                    }
                    is com.example.domain.RequestResult.Success -> {
                        val parsed = resultStatus.data
                        if (parsed.error == "bukan_struk") {
                            addAiMessage("Maaf, gambar yang dikirim sepertinya bukan struk belanjaan yang valid atau tidak dapat dibaca dengan jelas. Silakan coba unggah foto struk yang lebih terang dan jelas ya 😊")
                        } else {
                            showReceiptConfirmation(parsed)
                        }
                    }
                }
            } finally {
                _isAiProcessing.value = false
                // Bitmap besar selesai dipakai (compress+base64 terjadi di dalam
                // parseReceipt); lepaskan sekarang, jangan menunggu GC.
                if (bitmap !== thumbnail && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun showReceiptConfirmation(parsed: ParsedReceipt) {
        _pendingReceiptConfirmation.value = parsed
        
        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(parsed.dateMillis))
        val breakdown = java.lang.StringBuilder()
        breakdown.append("Aku berhasil membaca bukti transaksi dari ${parsed.title} (Tanggal: $dateStr)!\nTotal nominal: ${FormatUtils.formatRupiah(parsed.total)}.\n\nBerikut rincian transaksinya:\n")
        
        parsed.grouped.forEach { grp ->
            val emoji = grp.emoji
            val typeStr = if (grp.type == "income") "[Pemasukan]" else "[Pengeluaran]"
            breakdown.append("- $emoji $typeStr ${grp.category} (${grp.description}): ${FormatUtils.formatRupiah(grp.amount)}\n")
        }
        
        // Kategori hasil bacaan struk yang tidak cocok dengan kategori milik user
        // akan masuk ke kategori penampung, bukan dibuatkan kategori baru.
        // Dicek dari hasil pencocokan, bukan dari beda nama: kategori user bisa
        // punya emoji, jadi namanya tidak akan sama persis dengan jawaban AI
        // meskipun sebenarnya sudah cocok.
        val kePenampung = parsed.grouped
            .filter { grp ->
                val tersedia = if (grp.type == "income") incomeCategories.value else expenseCategories.value
                com.example.domain.CategoryResolver.resolve(grp.category, tersedia) is
                    com.example.domain.CategoryResolution.NeedsNewCategory
            }
            .map { it.category }
            .distinct()
        if (kePenampung.isNotEmpty()) {
            breakdown.append(
                "\n💡 Yang tidak punya kategori yang cocok, jadi masuk ke $CATCH_ALL_CATEGORY: " +
                    kePenampung.joinToString(", ")
            )
        }
        
        breakdown.append("\nApakah kamu ingin mencatat transaksi di atas?")
        
        addAiMessage(breakdown.toString())
    }

    fun confirmPendingReceipt() {
        val pending = _pendingReceiptConfirmation.value ?: return
        _pendingReceiptConfirmation.value = null

        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage("Ya, simpan"))
        _messages.value = currentList

        viewModelScope.launch {
            var count = 0
            pending.grouped.forEach { grp ->
                if (grp.amount > 0L) {
                    val recordCategory = resolveCategory(grp.type, grp.category)

                    val isFewItems = pending.items.size <= 3
                    val recordDesc = if (isFewItems && grp.description.isNotEmpty()) {
                        grp.description
                    } else {
                        "${pending.title} - $recordCategory"
                    }
                    val recordNotes = if (isFewItems) {
                        pending.title
                    } else {
                        grp.description
                    }

                    val record = FinancialRecord(
                        description = recordDesc,
                        amount = grp.amount,
                        type = grp.type,
                        category = recordCategory,
                        date = pending.dateMillis,
                        notes = recordNotes
                    )
                    repository.insert(record)
                    count++
                }
            }
            if (count > 0) pushPendingToCloud()
            addAiMessage("Berhasil mencatat transaksi dari ${pending.title} ke data keuangan Anda! 🎉")
        }
    }

    /** Dorong antrean PENDING ke Firestore setelah tulis lokal (diamkan bila gagal). */
    private suspend fun pushPendingToCloud() {
        if (!com.example.util.FirebaseSyncHelper.isUserSignedIn()) return
        runCatching { com.example.util.FirebaseSyncHelper.pushPending(application) }
    }

    fun cancelPendingReceipt() {
        _pendingReceiptConfirmation.value = null

        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage("Batal"))
        _messages.value = currentList

        addAiMessage("Siap, rekaman struk belanja dibatalkan 👍")
    }

    private val useCase = com.example.domain.TransactionParserUseCase()

    fun sendMessage(userText: String) {
        val now = System.currentTimeMillis()
        if (now - lastRequestTime < 600L || _isAiProcessing.value) return
        if (userText.trim().isEmpty()) return
        lastRequestTime = now

        // 1. Tambahkan pesan user ke list, lalu minta jawaban AI
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage(userText))
        _messages.value = currentList

        runAiRequest(userText)
    }

    /**
     * Mengulang permintaan AI yang gagal memakai teks yang sama.
     *
     * Gelembung error dibuang supaya percakapan tidak menumpuk pesan kegagalan,
     * dan gelembung pesan user TIDAK ditambahkan lagi: teks yang diulang adalah
     * pesan yang sudah ada di layar.
     */
    fun retryMessage(failed: ChatMessage.AiMessage) {
        val userText = failed.retryText ?: return
        if (_isAiProcessing.value) return

        val currentList = _messages.value.toMutableList()
        currentList.remove(failed)
        _messages.value = currentList

        runAiRequest(userText)
    }

    /**
     * Menjalankan permintaan parsing ke AI beserta indikator prosesnya.
     *
     * Dipisah dari [sendMessage] supaya jalur "Coba Lagi" memakai kode yang sama
     * persis tanpa menambah gelembung pesan user baru.
     */
    private fun runAiRequest(userText: String) {
        _isAiProcessing.value = true

        // 2. Tampilkan typing indicator
        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.TypingIndicator)
        _messages.value = listWithTyping

        aiJob = viewModelScope.launch {
            try {
            val pendingContext = _pendingClarificationContext.value

            // Satu-satunya titik pilih otak. Sisanya -- indikator proses, cabang
            // klarifikasi/bukan transaksi, dialog konfirmasi, penyaring kategori,
            // penyimpanan -- dipakai bersama, jadi jalur Gemini tidak berubah.
            val resultStatus = when (_aiMode.value) {
                AiMode.LOCAL -> localParser.parseTransaction(
                    userText = userText,
                    pendingContext = pendingContext,
                    incomeCategories = incomeCategories.value,
                    expenseCategories = expenseCategories.value
                )

                AiMode.GEMINI -> {
                    val apiKeyRaw = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" } ?: ""
                    val apiKey = if (apiKeyRaw.isEmpty() || apiKeyRaw == "MY_GEMINI_API_KEY" || apiKeyRaw == "GEMINI_API_KEY") {
                        "CF_PROXY_KEY"
                    } else {
                        apiKeyRaw
                    }

                    useCase.parseTransaction(
                        apiKey = apiKey,
                        userText = userText,
                        pendingContext = pendingContext,
                        incomeCategories = incomeCategories.value,
                        expenseCategories = expenseCategories.value
                    )
                }
            }

            removeTypingIndicator()

            when (resultStatus) {
                is com.example.domain.RequestResult.Error -> {
                    val e = resultStatus.e
                    // Pesan dari use case sudah menerjemahkan penyebabnya (timeout,
                    // model ditolak, rate limit, dst). Pemetaan kedua di sini dulu
                    // membuat semuanya jadi "Terjadi kesalahan" yang tidak membantu.
                    val errorMessage = resultStatus.message.ifBlank {
                        if (e is IOException) {
                            "Gagal terhubung. Periksa koneksi internet Anda."
                        } else {
                            "Terjadi kesalahan. Silakan coba lagi atau gunakan tab 'Transaksi Baru'."
                        }
                    }
                    com.example.util.SecureLog.e("ChatVM", "AI request failed", e)
                    addAiMessage(
                        text = "$errorMessage\n\n(Catatan: Anda tetap dapat mencatat langsung melalui tab 'Transaksi Baru'.)",
                        isError = true,
                        retryText = userText
                    )
                }
                is com.example.domain.RequestResult.Success -> {
                    val result = resultStatus.data
                    when {
                        result.error == "bukan_transaksi" -> {
                            addAiMessage("Aku hanya bisa mencatat transaksi keuangan 😊")
                            _pendingClarificationContext.value = null
                            _clarificationAttempts.value = 0
                        }
                        result.error == "klarifikasi" || result.error == "tanya" -> {
                            val currentAttempts = _clarificationAttempts.value + 1
                            _clarificationAttempts.value = currentAttempts
                            if (currentAttempts >= 3) {
                                _pendingClarificationContext.value = null
                                _clarificationAttempts.value = 0
                                addAiMessage("Sepertinya aku kesulitan memahami transaksi ini. Coba catat manual lewat tab 'Transaksi Baru' ya 😊")
                            } else {
                                addAiMessage(result.pertanyaan ?: "Bisa tolong jelaskan kembali transaksimu?")
                                _pendingClarificationContext.value = pendingContext ?: userText
                            }
                        }
                        else -> {
                            showConfirmation(result, userText)
                        }
                    }
                }
            }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    /**
     * Daftar kategori aktif untuk satu tipe, dibaca langsung dari Room.
     *
     * Sengaja tidak memakai `.value` dari [incomeCategories]/[expenseCategories].
     * Kategori yang benar-benar tersimpan adalah keputusan akhir, jadi tidak
     * boleh bergantung pada apakah sebuah StateFlow sempat ter-cache atau belum.
     */
    private suspend fun activeCategories(type: String): List<String> =
        categoryRepository.getByType(type).map { it.name }

    /**
     * Menentukan kategori transaksi dari jawaban AI.
     *
     * AI tidak pernah boleh membuat kategori dari jawabannya. Kalau boleh, model
     * akan mengarang kategori umum dari pengetahuannya sendiri ("Belanja",
     * "Hiburan") lalu aplikasi menyimpannya sebagai kategori aktif. Akibatnya
     * kategori yang SUDAH DIHAPUS user muncul kembali, karena pengecekan duplikat
     * hanya membandingkan kategori aktif.
     *
     * Jadi jawaban yang tidak cocok langsung ke "Lainnya".
     *
     * Hanya kategori SESUAI TIPE yang dicocokkan. Kalau income dan expense digabung,
     * jawaban "Bonus" untuk sebuah pengeluaran akan nyangkut ke kategori income.
     */
    private suspend fun resolveCategory(type: String, aiCategory: String?): String {
        val available = activeCategories(type)

        return when (val result = com.example.domain.CategoryResolver.resolve(aiCategory, available)) {
            is com.example.domain.CategoryResolution.Matched -> result.category
            is com.example.domain.CategoryResolution.NeedsNewCategory ->
                catchAllCategoryName(type, available)
        }
    }

    /**
     * Nama kategori penampung untuk jawaban AI yang tidak cocok dengan kategori user.
     *
     * Kalau kategori ini tidak ada (user menghapusnya), namanya tetap dikembalikan
     * apa adanya. Itu disengaja: nama "Lainnya" sudah dipakai sebagai penanda di
     * beberapa tempat lain, termasuk saat kategori dihapus lalu transaksinya
     * dipindahkan. Membuatkan kategori ini diam-diem di belakang layar justru
     * membatalkan pilihan user.
     */
    private fun catchAllCategoryName(type: String, available: List<String>): String {
        val fallback = CATCH_ALL_CATEGORY
        return available.firstOrNull {
            com.example.domain.CategoryResolver.normalize(it) ==
                com.example.domain.CategoryResolver.normalize(fallback)
        } ?: fallback
    }

    private suspend fun saveAndConfirmTransaction(result: ExtractionResult, userText: String) {
        val recordType = if (result.type?.uppercase() == "PEMASUKAN" || result.type?.lowercase() == "income") "income" else "expense"
        val recordCategory = resolveCategory(recordType, result.category)

        val recordAmount = result.amount ?: 0L
        if (recordAmount <= 0L) {
            addAiMessage("Nominal tidak valid, coba tulis ulang ya 🙏")
            return
        }
        val recordDescription = result.description ?: "Transaksi AI"
        val recordDate = result.date ?: System.currentTimeMillis()

        val finalNotes = if (!result.notes.isNullOrBlank()) {
            result.notes
        } else {
            val cleanedUserText = userText.trim()
            if (cleanedUserText.length > 50) cleanedUserText.take(50) + "..." else cleanedUserText
        }

        val record = FinancialRecord(
            description = recordDescription,
            amount = recordAmount,
            type = recordType,
            category = recordCategory,
            date = recordDate,
            notes = finalNotes
        )

        // Simpan ke database
        val rowId = repository.insert(record)
        val savedRecord = record.copy(id = rowId.toInt())
        pushPendingToCloud()

        // Format teks konfirmasi
        val displayType = if (recordType == "income") "Pemasukan" else "Pengeluaran"
        val readableAmount = FormatUtils.formatRupiah(recordAmount)
        val confirmText = "Berhasil mencatat $displayType: $recordDescription sebesar $readableAmount!"

        addAiMessage(confirmText, savedRecord)
        
        // Reset pending clarification context setelah transaksi sukses
        _pendingClarificationContext.value = null
        _clarificationAttempts.value = 0
    }

    private fun removeTypingIndicator() {
        val current = _messages.value.toMutableList()
        current.removeAll { it is ChatMessage.TypingIndicator || it is ChatMessage.ScanningReceiptIndicator }
        _messages.value = current
    }

    private fun addAiMessage(
        text: String,
        record: FinancialRecord? = null,
        isError: Boolean = false,
        retryText: String? = null
    ) {
        val current = _messages.value.toMutableList()
        current.add(ChatMessage.AiMessage(text, record, isError, retryText))
        _messages.value = current
    }

    private fun showConfirmation(result: ExtractionResult, userText: String) {
        _pendingConfirmation.value = result
        
        val dateVal = result.date ?: System.currentTimeMillis()
        val tanggal = java.text.SimpleDateFormat(
            "EEE, dd MMM yyyy", 
            java.util.Locale.forLanguageTag("id")
        ).format(java.util.Date(dateVal))
        
        val rawType = result.type?.lowercase() ?: "expense"
        val tipe = if (rawType == "pemasukan" || rawType == "income") "Pemasukan" else "Pengeluaran"
        val nominal = FormatUtils.formatRupiah(result.amount ?: 0L)
        
        val allCategories = incomeCategories.value + expenseCategories.value
        val resultCategoryRaw = result.category?.trim() ?: ""
        val (_, cleanResult) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(resultCategoryRaw)
        val cleanResultCategory = cleanResult.ifBlank { resultCategoryRaw }.trim()
        val recordCategory = allCategories.firstOrNull { 
            val (_, cleanCat) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
            cleanCat.trim().equals(cleanResultCategory, ignoreCase = true) 
        } ?: "Lainnya"
        
        addAiMessage(
            """
            Mau catat ini?
            📝 ${result.description ?: "Transaksi"}
            💰 $tipe $nominal
            🏷️ $recordCategory
            📅 $tanggal
            
            Silakan pilih tombol opsi 'Ya, Simpan' atau 'Batal' di bawah ini.
            """.trimIndent()
        )
    }




    companion object {
        /**
         * Kategori penampung untuk jawaban AI yang tidak cocok dengan kategori user.
         *
         * Namanya sama dengan yang dipakai FinancialTrackerViewModel saat kategori
         * dihapus lalu transaksinya dipindahkan, jadi cukup satu nama penanda di
         * seluruh aplikasi.
         */
        private const val CATCH_ALL_CATEGORY = "Lainnya"

        /** Kunci prefs untuk pilihan otak chat (lihat [AiMode]). */
        private const val PREF_AI_MODE = "chat_ai_mode"

        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = FinancialRecordRepository.create(database)
                return ChatViewModel(application, repository) as T
            }
        }
    }
}


