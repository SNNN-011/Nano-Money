package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

import android.graphics.Bitmap
import com.example.domain.ParsedReceipt

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

    private val prefs = application.getSharedPreferences("financial_tracker_prefs", android.content.Context.MODE_PRIVATE)

    private val _incomeCategories = MutableStateFlow<List<String>>(
        prefs.getString("income_categories_list", "Gaji,Investasi,Freelance,Lainnya")
            ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Gaji", "Investasi", "Freelance", "Lainnya")
    )
    val incomeCategories: StateFlow<List<String>> = _incomeCategories.asStateFlow()

    private val _expenseCategories = MutableStateFlow<List<String>>(
        prefs.getString("expense_categories_list", "Makanan,Transportasi,Tagihan,Hiburan,Belanja,Lainnya")
            ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")
    )
    val expenseCategories: StateFlow<List<String>> = _expenseCategories.asStateFlow()

    private fun saveIncomeCategories(list: List<String>) {
        val cleanedList = list.map {  com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second.ifBlank { it }.trim() }.distinct()
        _incomeCategories.value = cleanedList
        prefs.edit().putString("income_categories_list", cleanedList.joinToString(",")).apply()
    }

    private fun saveExpenseCategories(list: List<String>) {
        val cleanedList = list.map {  com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second.ifBlank { it }.trim() }.distinct()
        _expenseCategories.value = cleanedList
        prefs.edit().putString("expense_categories_list", cleanedList.joinToString(",")).apply()
    }

    fun addCategory(type: String, category: String): Boolean {
        val (_, cleanName) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(category.trim())
        val trimmed = cleanName.ifBlank { category.trim() }.trim()
        if (trimmed.isEmpty()) return false
        if (type == "income") {
            val current = _incomeCategories.value
            if (current.any {
                val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                c.trim().equals(trimmed, ignoreCase = true)
            }) return false
            saveIncomeCategories(current + trimmed)
        } else {
            val current = _expenseCategories.value
            if (current.any {
                val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                c.trim().equals(trimmed, ignoreCase = true)
            }) return false
            saveExpenseCategories(current + trimmed)
        }
        return true
    }

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "income_categories_list") {
            _incomeCategories.value = sharedPreferences.getString("income_categories_list", "Gaji,Investasi,Freelance,Lainnya")
                ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Gaji", "Investasi", "Freelance", "Lainnya")
        } else if (key == "expense_categories_list") {
            _expenseCategories.value = sharedPreferences.getString("expense_categories_list", "Makanan,Transportasi,Tagihan,Hiburan,Belanja,Lainnya")
                ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
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

    private val _selectedModel = MutableStateFlow(AiModelConfig.GEMMA_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
    }

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

    private val receiptUseCase = com.example.domain.ReceiptParserUseCase()

    fun scanReceipt(bitmap: Bitmap) {
        // Tampilkan scanning receipt indicator
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserImageMessage(bitmap))
        _messages.value = currentList

        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.ScanningReceiptIndicator)
        _messages.value = listWithTyping

        viewModelScope.launch {
            val apiKeyRaw = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" } ?: ""
            val apiKey = if (apiKeyRaw.isEmpty() || apiKeyRaw == "MY_GEMINI_API_KEY" || apiKeyRaw == "GEMINI_API_KEY") {
                "CF_PROXY_KEY"
            } else {
                apiKeyRaw
            }

            val resultStatus = receiptUseCase.parseReceipt(
                apiKey = apiKey,
                bitmap = bitmap,
                expenseCategories = _expenseCategories.value
            )

            removeTypingIndicator()

            when (resultStatus) {
                is com.example.domain.RequestResult.Error -> {
                    addAiMessage("Gagal memproses struk: ${resultStatus.message}")
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
        
        val existingCats = (_expenseCategories.value + _incomeCategories.value).map { cat ->
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
                if (grp.amount > 0.0) {
                    val (_, cleanCatNameRaw) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(grp.category.trim())
                    val cleanCatName = cleanCatNameRaw.ifBlank { grp.category }.trim()
                    val allCategories = _incomeCategories.value + _expenseCategories.value
                    
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

                    val record = FinancialRecord(
                        description = "${pending.title} - $finalCategoryName",
                        amount = grp.amount,
                        type = grp.type,
                        category = recordCategory,
                        date = pending.dateMillis,
                        notes = grp.description
                    )
                    val insertedId = repository.insert(record)
                    val insertedRecord = record.copy(id = insertedId.toInt())
                    com.example.util.FirebaseSyncHelper.uploadRecordToFirestoreDirectly(insertedRecord)
                    count++
                }
            }
            addAiMessage("Berhasil mencatat transaksi dari ${pending.title} ke data keuangan Anda! 🎉")
        }
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
        if (userText.trim().isEmpty()) return

        // 1. Tambahkan pesan user ke list
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage.UserMessage(userText))
        _messages.value = currentList

        // 2. Tampilkan typing indicator
        val listWithTyping = _messages.value.toMutableList()
        listWithTyping.add(ChatMessage.TypingIndicator)
        _messages.value = listWithTyping

        viewModelScope.launch {
            val modelName = _selectedModel.value

            val apiKeyRaw = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" } ?: ""
            val apiKey = if (apiKeyRaw.isEmpty() || apiKeyRaw == "MY_GEMINI_API_KEY" || apiKeyRaw == "GEMINI_API_KEY") {
                "CF_PROXY_KEY"
            } else {
                apiKeyRaw
            }

            val pendingContext = _pendingClarificationContext.value

            val resultStatus = useCase.parseTransaction(
                apiKey = apiKey,
                modelName = modelName,
                userText = userText,
                pendingContext = pendingContext,
                incomeCategories = _incomeCategories.value,
                expenseCategories = _expenseCategories.value
            )

            removeTypingIndicator()

            when (resultStatus) {
                is com.example.domain.RequestResult.Error -> {
                    val e = resultStatus.e
                    val errorMessage = if (e is IOException) {
                        "Gagal terhubung. Periksa koneksimu ya 🙏 (Detail: ${e.javaClass.simpleName} - ${e.message})"
                    } else if (e is retrofit2.HttpException) {
                        val errorBodyText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try { e.response()?.errorBody()?.string() } catch (ioe: java.io.IOException) { "gagal" }
                        }
                        if (!errorBodyText.isNullOrEmpty() && errorBodyText.contains("not found", ignoreCase = true)) {
                            "HTTP ${e.code()}: AI sedang ada kendala, coba kembali atau gunakan opsi Gemini."
                        } else {
                            "HTTP ${e.code()}: ${errorBodyText ?: "tidak ada detail error"}"
                        }
                    } else {
                        if (e != null) {
                            val sw = java.io.StringWriter()
                            e.printStackTrace(java.io.PrintWriter(sw))
                            val fullTrace = sw.toString()
                            "Exception: ${e.javaClass.simpleName} - ${e.localizedMessage ?: e.message}\nCause: ${e.cause?.javaClass?.simpleName} - ${e.cause?.localizedMessage ?: e.cause?.message}\nDetails: ${fullTrace.take(450)}"
                        } else {
                            resultStatus.message
                        }
                    }
                    addAiMessage("Detail Error: $errorMessage\n\n(Catatan: Anda tetap dapat mencatat langsung melalui tab 'Transaksi Baru'.)")
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
        }
    }

    private suspend fun saveAndConfirmTransaction(result: ExtractionResult, userText: String) {
        val recordType = if (result.type?.uppercase() == "PEMASUKAN" || result.type?.lowercase() == "income") "income" else "expense"
        val allCategories = _incomeCategories.value + _expenseCategories.value
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

        val recordAmount = result.amount ?: 0.0
        if (recordAmount <= 0.0) {
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
        com.example.util.FirebaseSyncHelper.uploadRecordToFirestoreDirectly(savedRecord)

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
        val nominal = FormatUtils.formatRupiah(result.amount ?: 0.0)
        
        val allCategories = _incomeCategories.value + _expenseCategories.value
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
                val repository = FinancialRecordRepository(database.financialRecordDao())
                return ChatViewModel(application, repository) as T
            }
        }
    }
}
