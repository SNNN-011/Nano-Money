package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.CategoryMigration
import com.example.data.CategoryRepository
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
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
        val record: FinancialRecord? = null
    ) : ChatMessage()
    object TypingIndicator : ChatMessage()
    object ScanningReceiptIndicator : ChatMessage()
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

    val incomeCategories: StateFlow<List<String>> = categoryRepository.observeByType("income")
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<String>> = categoryRepository.observeByType("expense")
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nama kategori yang sudah ada, untuk pengecekan duplikat sebelum menambah. */
    private suspend fun knownCategoryNames(): Set<String> =
        (incomeCategories.value + expenseCategories.value)
            .mapTo(mutableSetOf()) { com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second.trim() }

    /**
     * Menambah kategori baru. Id, updatedAt, dan status sync diisi repository.
     *_duplikat dicek dari daftar Room yang sudah ada, bukan dari prefs._
     */
    suspend fun addCategory(type: String, category: String): Boolean {
        val (_, cleanName) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(category.trim())
        val trimmed = cleanName.ifBlank { category.trim() }.trim()
        if (trimmed.isEmpty()) return false

        val normalized = trimmed.lowercase()
        if (knownCategoryNames().any { it.lowercase() == normalized }) return false

        val normalizedType = if (type == "income") CategoryMigration.TYPE_INCOME else CategoryMigration.TYPE_EXPENSE
        return categoryRepository.add(normalizedType, trimmed) != null
    }

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

    private val receiptUseCase = com.example.domain.ReceiptParserUseCase()

    fun scanReceipt(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastRequestTime < 600L || _isAiProcessing.value) return
        lastRequestTime = now
        _isAiProcessing.value = true

        // Tampilkan scanning receipt indicator
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserImageMessage(bitmap))
        _messages.value = currentList

        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.ScanningReceiptIndicator)
        _messages.value = listWithTyping

        viewModelScope.launch {
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
                        addAiMessage(msg)
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
        
        val existingCats = (expenseCategories.value + incomeCategories.value).map { cat ->
            val (_, clean) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(cat)
            clean.ifBlank { cat }.trim().lowercase()
        }
        val newCats = parsed.grouped.map { it.category }.filter { cat ->
            val (_, clean) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(cat)
            !existingCats.contains(clean.ifBlank { cat }.trim().lowercase())
        }.distinct()
        if (newCats.isNotEmpty()) {
            breakdown.append("\n💡 *Kategori baru otomatis ditambahkan:* ${newCats.joinToString(", ")}")
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
                    val (_, cleanCatNameRaw) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(grp.category.trim())
                    val cleanCatName = cleanCatNameRaw.ifBlank { grp.category }.trim()
                    val allCategories = incomeCategories.value + expenseCategories.value
                    
                    val matched = allCategories.firstOrNull { 
                        val (_, cleanMatched) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                        cleanMatched.trim().equals(cleanCatName, ignoreCase = true)
                    }
                    
                    val finalCategoryName = if (matched == null) {
                        addCategory(grp.type, cleanCatName)
                        cleanCatName
                    } else {
                        val (_, cleanMatched) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(matched)
                        cleanMatched.trim()
                    }

                    val recordCategory = finalCategoryName

                    val isFewItems = pending.items.size <= 3
                    val recordDesc = if (isFewItems && grp.description.isNotEmpty()) {
                        grp.description
                    } else {
                        "${pending.title} - $finalCategoryName"
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
        _isAiProcessing.value = true

        // 1. Tambahkan pesan user ke list
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage(userText))
        _messages.value = currentList

        // 2. Tampilkan typing indicator
        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.TypingIndicator)
        _messages.value = listWithTyping

        viewModelScope.launch {
            try {
            val apiKeyRaw = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" } ?: ""
            val apiKey = if (apiKeyRaw.isEmpty() || apiKeyRaw == "MY_GEMINI_API_KEY" || apiKeyRaw == "GEMINI_API_KEY") {
                "CF_PROXY_KEY"
            } else {
                apiKeyRaw
            }

            val pendingContext = _pendingClarificationContext.value

            val resultStatus = useCase.parseTransaction(
                apiKey = apiKey,
                userText = userText,
                pendingContext = pendingContext,
                incomeCategories = incomeCategories.value,
                expenseCategories = expenseCategories.value
            )

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
                    addAiMessage("$errorMessage\n\n(Catatan: Anda tetap dapat mencatat langsung melalui tab 'Transaksi Baru'.)")
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

    private suspend fun saveAndConfirmTransaction(result: ExtractionResult, userText: String) {
        val recordType = if (result.type?.uppercase() == "PEMASUKAN" || result.type?.lowercase() == "income") "income" else "expense"
        val allCategories = incomeCategories.value + expenseCategories.value
        val resultCategory = result.category?.trim() ?: ""

        val categoryNameOnly = if (resultCategory.isNotEmpty()) {
            val (_, cleanResultRaw) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(resultCategory)
            val cleanResult = cleanResultRaw.ifBlank { resultCategory }.trim()
            val matched = allCategories.firstOrNull { 
                val (_, cleanMatched) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                cleanMatched.trim().equals(cleanResult, ignoreCase = true)
            }
            if (matched == null) {
                addCategory(recordType, cleanResult)
                cleanResult
            } else {
                val (_, cleanMatched) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(matched)
                cleanMatched.trim()
            }
        } else {
            "Lainnya"
        }

        val recordCategory = categoryNameOnly

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

    private fun addAiMessage(text: String, record: FinancialRecord? = null) {
        val current = _messages.value.toMutableList()
        current.add(ChatMessage.AiMessage(text, record))
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


