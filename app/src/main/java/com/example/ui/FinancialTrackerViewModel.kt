package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import com.example.data.RecurringTransaction
import com.example.data.RecurringTransactionDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

class FinancialTrackerViewModel(
    application: Application,
    private val repository: FinancialRecordRepository
) : AndroidViewModel(application) {

    // Filter states
    private val _filterType = MutableStateFlow("Semua") // "Semua", "Pendapatan", "Pengeluaran"
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _sortByNewest = MutableStateFlow(true) // true = newest, false = oldest
    val sortByNewest: StateFlow<Boolean> = _sortByNewest.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateFilterType(type: String) {
        _filterType.value = type
    }

    fun toggleSortByNewest() {
        _sortByNewest.value = !_sortByNewest.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Raw records from database
    val allRecords: StateFlow<List<FinancialRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val recurringDao = AppDatabase.getDatabase(application).recurringTransactionDao()

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = recurringDao.getAllRecurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined filtered & sorted records
    val filteredRecords: StateFlow<List<FinancialRecord>> = combine(
        allRecords, filterType, sortByNewest, searchQuery
    ) { records, filter, isNewest, query ->
        val queryLower = query.trim().lowercase()
        val searched = if (queryLower.isEmpty()) records else records.filter {
            it.description.lowercase().contains(queryLower) ||
            it.category.lowercase().contains(queryLower) ||
            it.notes.lowercase().contains(queryLower)
        }
        val filtered = when (filter) {
            "Pendapatan" -> searched.filter { it.type == "income" }
            "Pengeluaran" -> searched.filter { it.type == "expense" }
            else -> searched
        }
        if (isNewest) {
            filtered.sortedByDescending { it.date }
        } else {
            filtered.sortedBy { it.date }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Form inputs state
    var editingRecordId: Int? = null
        private set

    val formDescription = MutableStateFlow("")
    val formAmount = MutableStateFlow("")
    val formType = MutableStateFlow("income") // "income" or "expense"
    val formCategory = MutableStateFlow("Gaji") // Default based on type
    val formDate = MutableStateFlow(System.currentTimeMillis())
    val formNotes = MutableStateFlow("")

    // Form validation messages (empty/null means valid)
    val descriptionError = MutableStateFlow<String?>(null)
    val amountError = MutableStateFlow<String?>(null)
    val dateError = MutableStateFlow<String?>(null)

    // General messages (Toast or Alerts)
    val uiEvent = MutableSharedFlow<UiEvent>()

    sealed interface UiEvent {
        data class ShowToast(val message: String) : UiEvent
        data class ShowAlert(val title: String, val message: String) : UiEvent
    }

    private val prefs = application.getSharedPreferences("financial_tracker_prefs", android.content.Context.MODE_PRIVATE)

    val incomeCategories = MutableStateFlow<List<String>>(
        prefs.getString("income_categories_list", "Gaji,Investasi,Freelance,Lainnya")
            ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Gaji", "Investasi", "Freelance", "Lainnya")
    )
    val expenseCategories = MutableStateFlow<List<String>>(
        prefs.getString("expense_categories_list", "Makanan,Transportasi,Tagihan,Hiburan,Belanja,Lainnya")
            ?.split(",")?.filter { it.isNotEmpty() } ?: listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")
    )

    val monthlyBudgetLimit = MutableStateFlow(prefs.getFloat("monthly_budget_limit", 0f).toDouble())
    val selectedBudgetOffset = MutableStateFlow(0)

    fun changeBudgetMonthOffset(offset: Int) {
        selectedBudgetOffset.value = offset
    }

    fun updateMonthlyBudgetLimit(limit: Double) {
        monthlyBudgetLimit.value = limit
        prefs.edit().putFloat("monthly_budget_limit", limit.toFloat()).apply()
    }

    val monthlySpendingTotal: StateFlow<Double> = combine(allRecords, selectedBudgetOffset) { records, offset ->
        val currentCal = java.util.Calendar.getInstance()
        currentCal.add(java.util.Calendar.MONTH, offset)
        val recordCal = java.util.Calendar.getInstance()
        records.filter { it.type == "expense" }.sumOf { record ->
            recordCal.timeInMillis = record.date
            if (recordCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) &&
                recordCal.get(java.util.Calendar.MONTH) == currentCal.get(java.util.Calendar.MONTH)) {
                record.amount
            } else {
                0.0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncome: StateFlow<Double> = combine(allRecords, selectedBudgetOffset) { records, offset ->
        val currentCal = java.util.Calendar.getInstance()
        currentCal.add(java.util.Calendar.MONTH, offset)
        val recordCal = java.util.Calendar.getInstance()
        records.filter { it.type == "income" }.sumOf { record ->
            recordCal.timeInMillis = record.date
            if (recordCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) &&
                recordCal.get(java.util.Calendar.MONTH) == currentCal.get(java.util.Calendar.MONTH)) {
                record.amount
            } else {
                0.0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = combine(allRecords, selectedBudgetOffset) { records, offset ->
        val currentCal = java.util.Calendar.getInstance()
        currentCal.add(java.util.Calendar.MONTH, offset)
        val recordCal = java.util.Calendar.getInstance()
        records.filter { it.type == "expense" }.sumOf { record ->
            recordCal.timeInMillis = record.date
            if (recordCal.get(java.util.Calendar.YEAR) == currentCal.get(java.util.Calendar.YEAR) &&
                recordCal.get(java.util.Calendar.MONTH) == currentCal.get(java.util.Calendar.MONTH)) {
                record.amount
            } else {
                0.0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentBalance: StateFlow<Double> = combine(totalIncome, totalExpense) { income, expense ->
        income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        // Automatically keep category logic sensible when type changes
        formType.onEach { type ->
            if (type == "income") {
                if (!incomeCategories.value.contains(formCategory.value)) {
                    formCategory.value = incomeCategories.value.firstOrNull() ?: "Gaji"
                }
            } else {
                if (!expenseCategories.value.contains(formCategory.value)) {
                    formCategory.value = expenseCategories.value.firstOrNull() ?: "Makanan"
                }
            }
        }.launchIn(viewModelScope)

        // One-time cleanup for old categories that were saved with emojis:
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val hasCleaned = prefs.getBoolean("has_cleaned_emojis_categories", false)
            if (!hasCleaned) {
                try {
                    val rawRecordsFlow = repository.allRecords
                    val records = rawRecordsFlow.first()
                    for (record in records) {
                        val (_, cleanCategory) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(record.category)
                        if (record.category != cleanCategory && cleanCategory.isNotBlank()) {
                            repository.update(record.copy(category = cleanCategory))
                        }
                    }
                    prefs.edit().putBoolean("has_cleaned_emojis_categories", true).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        checkAndProcessRecurringTransactions()
    }

    /**
     * Debug helper to seed sample transaction data when database is empty.
     */
    fun seedSampleData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (repository.getRecordCount() == 0) {
                    val sampleData = mutableListOf<FinancialRecord>()
                    val cal = java.util.Calendar.getInstance()

                    // --- CURRENT MONTH ---
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 10)
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000.0, "income", "Gaji", cal.timeInMillis, "Gaji pokok"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 8)
                    sampleData.add(FinancialRecord(0, "Makan Siang", 25000.0, "expense", "Makanan", cal.timeInMillis, "Bakso"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 5)
                    sampleData.add(FinancialRecord(0, "Beli Buku", 150000.0, "expense", "Belanja", cal.timeInMillis, "Buku Android"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 3)
                    sampleData.add(FinancialRecord(0, "Transportasi Gojek", 20000.0, "expense", "Transportasi", cal.timeInMillis, "Ke kantor"))

                    // --- LAST MONTH (1 Month Ago) ---
                    val lastMonthCal = java.util.Calendar.getInstance()
                    lastMonthCal.add(java.util.Calendar.MONTH, -1)
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 10)
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000.0, "income", "Gaji", lastMonthCal.timeInMillis, "Gaji pokok"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 15)
                    sampleData.add(FinancialRecord(0, "Belanja Sepatu Baru", 450000.0, "expense", "Belanja", lastMonthCal.timeInMillis, "Sepatu lari"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 18)
                    sampleData.add(FinancialRecord(0, "Makan Malam Bersama", 180000.0, "expense", "Makanan", lastMonthCal.timeInMillis, "Restoran"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 22)
                    sampleData.add(FinancialRecord(0, "Langganan Netflix", 54000.0, "expense", "Hiburan", lastMonthCal.timeInMillis, "Paket Standard"))

                    // --- 2 MONTHS AGO ---
                    val twoMonthsAgoCal = java.util.Calendar.getInstance()
                    twoMonthsAgoCal.add(java.util.Calendar.MONTH, -2)
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 10)
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000.0, "income", "Gaji", twoMonthsAgoCal.timeInMillis, "Gaji pokok"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 5)
                    sampleData.add(FinancialRecord(0, "Servis Motor", 350000.0, "expense", "Tagihan", twoMonthsAgoCal.timeInMillis, "Ganti oli & ban"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 12)
                    sampleData.add(FinancialRecord(0, "Makan Bakso Lapangan", 30000.0, "expense", "Makanan", twoMonthsAgoCal.timeInMillis, ""))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 20)
                    sampleData.add(FinancialRecord(0, "Tagihan Internet Wifi", 150000.0, "expense", "Tagihan", twoMonthsAgoCal.timeInMillis, "Bulanan"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 25)
                    sampleData.add(FinancialRecord(0, "Tiket Bioskop", 65000.0, "expense", "Hiburan", twoMonthsAgoCal.timeInMillis, "Nonton film"))

                    sampleData.forEach { repository.insert(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveIncomeCategories(list: List<String>) {
        val cleanedList = list.map {  com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second.ifBlank { it }.trim() }.distinct()
        incomeCategories.value = cleanedList
        prefs.edit().putString("income_categories_list", cleanedList.joinToString(",")).apply()
    }

    private fun saveExpenseCategories(list: List<String>) {
        val cleanedList = list.map {  com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it).second.ifBlank { it }.trim() }.distinct()
        expenseCategories.value = cleanedList
        prefs.edit().putString("expense_categories_list", cleanedList.joinToString(",")).apply()
    }

    fun addCategory(type: String, category: String): Boolean {
        val (_, cleanName) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(category.trim())
        val trimmed = cleanName.ifBlank { category.trim() }.trim()
        if (trimmed.isEmpty()) return false
        if (type == "income") {
            val current = incomeCategories.value
            if (current.any {
                val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                c.trim().equals(trimmed, ignoreCase = true)
            }) return false
            saveIncomeCategories(current + trimmed)
        } else {
            val current = expenseCategories.value
            if (current.any {
                val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                c.trim().equals(trimmed, ignoreCase = true)
            }) return false
            saveExpenseCategories(current + trimmed)
        }
        return true
    }

    fun deleteCategory(type: String, category: String): Boolean {
        val trimmed = category.trim()
        if (type == "income") {
            val current = incomeCategories.value
            if (!current.contains(trimmed)) return false
            if (current.size <= 1) return false
            val newList = current.filter { it != trimmed }
            saveIncomeCategories(newList)
            if (formCategory.value == trimmed) {
                formCategory.value = newList.firstOrNull() ?: ""
            }
        } else {
            val current = expenseCategories.value
            if (!current.contains(trimmed)) return false
            if (current.size <= 1) return false
            val newList = current.filter { it != trimmed }
            saveExpenseCategories(newList)
            if (formCategory.value == trimmed) {
                formCategory.value = newList.firstOrNull() ?: ""
            }
        }
        return true
    }

    fun setEditingRecord(record: FinancialRecord?) {
        if (record != null) {
            editingRecordId = record.id
            formDescription.value = record.description
            if (record.amount % 1.0 == 0.0) {
                formAmount.value = record.amount.toLong().toString()
            } else {
                formAmount.value = record.amount.toString()
            }
            formType.value = record.type
            formCategory.value = record.category
            formDate.value = record.date
            formNotes.value = record.notes
            
            // Clear prior form errors
            clearFormErrors()
        } else {
            resetForm()
        }
    }

    fun resetForm() {
        editingRecordId = null
        formDescription.value = ""
        formAmount.value = ""
        formType.value = "income"
        formCategory.value = "Gaji"
        formDate.value = System.currentTimeMillis()
        formNotes.value = ""
        clearFormErrors()
    }

    private fun clearFormErrors() {
        descriptionError.value = null
        amountError.value = null
        dateError.value = null
    }

    fun validateAndSave(): Boolean {
        var isValid = true

        val desc = formDescription.value.trim()
        if (desc.isEmpty()) {
            descriptionError.value = "Deskripsi tidak boleh kosong"
            isValid = false
        } else {
            descriptionError.value = null
        }

        val amtStr = formAmount.value.trim()
        val amt = amtStr.toDoubleOrNull()
        if (amtStr.isEmpty()) {
            amountError.value = "Jumlah tidak boleh kosong"
            isValid = false
        } else if (amt == null || amt <= 0) {
            amountError.value = "Jumlah harus berupa angka positif"
            isValid = false
        } else {
            amountError.value = null
        }

        // Tanggal validation
        if (formDate.value <= 0) {
            dateError.value = "Tanggal tidak valid"
            isValid = false
        } else {
            dateError.value = null
        }

        if (!isValid) return false

        val record = FinancialRecord(
            id = editingRecordId ?: 0,
            description = desc,
            amount = amt ?: 0.0,
            type = formType.value,
            category = formCategory.value,
            date = formDate.value,
            notes = formNotes.value.trim()
        )

        viewModelScope.launch {
            try {
                if (editingRecordId != null) {
                    repository.update(record)
                    uiEvent.emit(UiEvent.ShowToast("Transaksi berhasil diperbarui!"))
                } else {
                    repository.insert(record)
                    uiEvent.emit(UiEvent.ShowToast("Transaksi baru berhasil disimpan!"))
                }
                resetForm()
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Menyimpan", "Gagal menyimpan transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
        return true
    }

    fun deleteRecord(record: FinancialRecord) {
        viewModelScope.launch {
            try {
                repository.delete(record)
                uiEvent.emit(UiEvent.ShowToast("Transaksi berhasil dihapus!"))
                if (editingRecordId == record.id) {
                    resetForm()
                }
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Menghapus", "Gagal menghapus transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
    }

    // Export validation and logic
    fun getExportJsonString(): String {
        val array = JSONArray()
        allRecords.value.forEach { record ->
            val obj = JSONObject()
            obj.put("description", record.description)
            obj.put("amount", record.amount)
            obj.put("type", record.type)
            obj.put("category", record.category)
            obj.put("date", record.date)
            obj.put("notes", record.notes)
            array.put(obj)
        }
        return array.toString(4) // Pretty print indent 4
    }

    private fun importCategoriesFromRecords(records: List<FinancialRecord>) {
        val currentIncome = incomeCategories.value.toMutableList()
        val currentExpense = expenseCategories.value.toMutableList()
        var modified = false
        for (record in records) {
            val (_, cat) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(record.category.trim())
            val cleanCat = cat.ifBlank { record.category.trim() }.trim()
            if (cleanCat.isNotEmpty()) {
                if (record.type == "income") {
                    if (!currentIncome.any {
                        val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                        c.trim().equals(cleanCat, ignoreCase = true)
                    }) {
                        currentIncome.add(cleanCat)
                        modified = true
                    }
                } else if (record.type == "expense") {
                    if (!currentExpense.any {
                        val (_, c) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(it)
                        c.trim().equals(cleanCat, ignoreCase = true)
                    }) {
                        currentExpense.add(cleanCat)
                        modified = true
                    }
                }
            }
        }
        if (modified) {
            if (currentIncome != incomeCategories.value) {
                saveIncomeCategories(currentIncome)
            }
            if (currentExpense != expenseCategories.value) {
                saveExpenseCategories(currentExpense)
            }
        }
    }

    // Import validation and logic
    fun importFromJsonString(jsonStr: String): Boolean {
        try {
            val array = JSONArray(jsonStr)
            val parsedList = mutableListOf<FinancialRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                
                // Fields validation
                val description = obj.optString("description", "").trim()
                if (description.isEmpty()) throw IllegalArgumentException("Deskripsi baris ${i+1} kosong.")

                val amount = obj.optDouble("amount", -1.0)
                if (amount <= 0 || amount.isNaN()) throw IllegalArgumentException("Jumlah baris ${i+1} harus angka positif.")

                val type = obj.optString("type", "")
                if (type != "income" && type != "expense") throw IllegalArgumentException("Tipe baris ${i+1} harus 'income' atau 'expense'.")

                val category = obj.optString("category", "").trim()
                if (category.isEmpty()) throw IllegalArgumentException("Kategori baris ${i+1} kosong.")

                val date = obj.optLong("date", -1)
                if (date <= 0) throw IllegalArgumentException("Tanggal baris ${i+1} tidak valid.")

                val notes = obj.optString("notes", "")

                parsedList.add(
                    FinancialRecord(
                        description = description,
                        amount = amount,
                        type = type,
                        category = category,
                        date = date,
                        notes = notes
                    )
                )
            }

            viewModelScope.launch {
                try {
                    repository.replaceAll(parsedList)
                    importCategoriesFromRecords(parsedList)
                    uiEvent.emit(UiEvent.ShowToast("Berhasil mengimpor ${parsedList.size} transaksi!"))
                } catch (e: Exception) {
                    uiEvent.emit(UiEvent.ShowAlert("Impor Gagal", "Gagal menyimpan data impor ke database: ${e.localizedMessage ?: "kesalahan database"}"))
                }
            }
            return true
        } catch (e: Exception) {
            viewModelScope.launch {
                uiEvent.emit(
                    UiEvent.ShowAlert(
                        title = "Impor Gagal",
                        message = "File tidak valid atau korup: ${e.localizedMessage ?: "struktur JSON salah"}"
                    )
                )
            }
            return false
        }
    }

    // Export Excel/CSV logic
    fun getExportCsvString(selectedMonths: List<YearMonth>): String {
        val sb = java.lang.StringBuilder()
        sb.append("Deskripsi,Jumlah,Tipe,Kategori,Tanggal,Catatan\n")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        
        val filtered = allRecords.value.filter { record ->
            val date = java.time.Instant.ofEpochMilli(record.date)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val recordYM = YearMonth.of(date.year, date.monthValue)
            selectedMonths.contains(recordYM)
        }.sortedBy { it.date }

        filtered.forEach { record ->
            fun escapeField(field: String): String {
                val clean = field.replace("\"", "\"\"")
                return "\"$clean\""
            }
            sb.append(escapeField(record.description)).append(",")
            sb.append(record.amount).append(",")
            val typeLabel = if (record.type == "income") "Pemasukan" else "Pengeluaran"
            sb.append(escapeField(typeLabel)).append(",")
            sb.append(escapeField(record.category)).append(",")
            val formattedDate = sdf.format(java.util.Date(record.date))
            sb.append(escapeField(formattedDate)).append(",")
            sb.append(escapeField(record.notes)).append("\n")
        }
        return sb.toString()
    }

    // Import from spreadsheet Excel / CSV
    fun importFromCsvString(csvStr: String): Boolean {
        try {
            val parsedList = com.example.domain.CsvImportUseCase().parseCsvString(csvStr)
            viewModelScope.launch {
                try {
                    repository.replaceAll(parsedList)
                    importCategoriesFromRecords(parsedList)
                    uiEvent.emit(UiEvent.ShowToast("Berhasil mengimpor ${parsedList.size} data transaksi dari Excel/CSV!"))
                } catch (e: Exception) {
                    uiEvent.emit(UiEvent.ShowAlert("Impor Gagal", "Gagal menyimpan data ke database: ${e.localizedMessage}"))
                }
            }
            return true
        } catch (e: Exception) {
            viewModelScope.launch {
                uiEvent.emit(
                    UiEvent.ShowAlert(
                        title = "Impor Gagal",
                        message = "Gagal memproses file Excel/CSV: ${e.localizedMessage ?: "Format tidak valid"}"
                    )
                )
            }
            return false
        }
    }

    // Generate Monthly PDF Report
    fun generatePdfReport(context: Context, outputStream: OutputStream, selectedMonths: List<YearMonth>) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var y = 60f

        fun drawPageHeader(canvas: Canvas, page: Int) {
            val hPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 9f
                color = android.graphics.Color.parseColor("#475569")
            }
            canvas.drawText("NANO MONEY - LAPORAN KEUANGAN", 40f, 35f, hPaint)
            
            hPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val pageStr = "Halaman $page"
            val textWidth = hPaint.measureText(pageStr)
            canvas.drawText(pageStr, 555f - textWidth, 35f, hPaint)
            
            hPaint.color = android.graphics.Color.parseColor("#E2E8F0")
            hPaint.strokeWidth = 1f
            canvas.drawLine(40f, 42f, 555f, 42f, hPaint)
        }

        fun checkPageBreak(requiredHeight: Float) {
            if (y + requiredHeight > 780f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                y = 60f
                drawPageHeader(canvas, pageNumber)
            }
        }

        val paint = Paint().apply { isAntiAlias = true }

        // Main Title Header for first page
        paint.color = android.graphics.Color.parseColor("#4F46E5") // Indigo Accent
        canvas.drawRect(40f, y, 55f, y + 25f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.color = android.graphics.Color.parseColor("#0F172A")
        canvas.drawText("NANO MONEY", 65f, y + 20f, paint)

        y += 35f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textSize = 12f
        paint.color = android.graphics.Color.parseColor("#475569")
        canvas.drawText("mencatat keuangan semudah chat dengan teman", 40f, y, paint)

        // Print date
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        val printDateStr = "Dicetak: " + FormatUtils.formatDate(System.currentTimeMillis())
        val dateWidth = paint.measureText(printDateStr)
        canvas.drawText(printDateStr, 555f - dateWidth, y, paint)

        y += 22f

        // Periods of report
        val formattedMonths = selectedMonths.sorted().joinToString(", ") { ym ->
            val monthName = when (ym.monthValue) {
                1 -> "Januari"
                2 -> "Februari"
                3 -> "Maret"
                4 -> "April"
                5 -> "Mei"
                6 -> "Juni"
                7 -> "Juli"
                8 -> "Agustus"
                9 -> "September"
                10 -> "Oktober"
                11 -> "November"
                12 -> "Desember"
                else -> ""
            }
            "$monthName ${ym.year}"
        }
        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("Laporan Keuangan Bulanan", 40f, y, paint)
        
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.parseColor("#64748B")
        var periodLabel = "Periode: $formattedMonths"
        if (paint.measureText(periodLabel) > 500f) {
            periodLabel = periodLabel.take(80) + "..."
        }
        canvas.drawText(periodLabel, 40f, y + 14f, paint)

        y += 24f
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        canvas.drawLine(40f, y, 555f, y, paint)

        // Filter and compute records
        val records = allRecords.value.filter { record ->
            val date = Instant.ofEpochMilli(record.date)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val recordYM = YearMonth.of(date.year, date.monthValue)
            selectedMonths.contains(recordYM)
        }.sortedBy { it.date }

        var totalIn = 0.0
        var totalOut = 0.0
        records.forEach { record ->
            if (record.type == "income") totalIn += record.amount else totalOut += record.amount
        }
        val netBalance = totalIn - totalOut

        y += 15f

        // Draw Summary Box background
        paint.color = android.graphics.Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, y, 555f, y + 60f, paint)

        // Summary Box border
        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(40f, y, 555f, y + 60f, paint)
        paint.style = Paint.Style.FILL

        // Total In
        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL PEMASUKAN", 60f, y + 20f, paint)
        paint.color = android.graphics.Color.parseColor("#15803D")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FormatUtils.formatRupiah(totalIn), 60f, y + 38f, paint)

        // Total Out
        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL PENGELUARAN", 230f, y + 20f, paint)
        paint.color = android.graphics.Color.parseColor("#B91C1C")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FormatUtils.formatRupiah(totalOut), 230f, y + 38f, paint)

        // Net Balance
        paint.color = android.graphics.Color.parseColor("#64748B")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("SALDO BERSIH", 400f, y + 20f, paint)
        paint.color = if (netBalance >= 0) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#B91C1C")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FormatUtils.formatRupiah(netBalance), 400f, y + 38f, paint)

        y += 75f

        // Table title
        paint.color = android.graphics.Color.parseColor("#0F172A")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DAFTAR TRANSAKSI DETAIL", 40f, y, paint)

        y += 12f

        // Drawing Table Headers
        paint.color = android.graphics.Color.parseColor("#334155")
        canvas.drawRect(40f, y, 555f, y + 20f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("No", 45f, y + 13f, paint)
        canvas.drawText("Tanggal", 75f, y + 13f, paint)
        canvas.drawText("Deskripsi", 145f, y + 13f, paint)
        canvas.drawText("Kategori", 300f, y + 13f, paint)
        canvas.drawText("Tipe", 390f, y + 13f, paint)
        val amountTitleWidth = paint.measureText("Jumlah")
        canvas.drawText("Jumlah", 550f - amountTitleWidth, y + 13f, paint)

        y += 20f

        if (records.isEmpty()) {
            checkPageBreak(30f)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textSize = 10f
            paint.color = android.graphics.Color.parseColor("#64748B")
            canvas.drawText("Tidak ada transaksi untuk bulan yang dipilih.", 45f, y + 15f, paint)
        } else {
            records.forEachIndexed { idx, rec ->
                checkPageBreak(24f)

                // Alternating rows bg
                if (idx % 2 == 1) {
                    val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#F8FAFC") }
                    canvas.drawRect(40f, y, 555f, y + 20f, bgPaint)
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.5f
                paint.color = android.graphics.Color.parseColor("#334155")

                // Index
                canvas.drawText((idx + 1).toString(), 45f, y + 13f, paint)

                // Date
                canvas.drawText(FormatUtils.formatDate(rec.date), 75f, y + 13f, paint)

                // Description
                var desc = rec.description
                if (paint.measureText(desc) > 145f) {
                    while (paint.measureText("$desc...") > 140f && desc.length > 1) {
                        desc = desc.dropLast(1)
                    }
                    desc = "$desc..."
                }
                canvas.drawText(desc, 145f, y + 13f, paint)

                // Category
                var cat = rec.category
                if (paint.measureText(cat) > 85f) {
                    while (paint.measureText("$cat...") > 80f && cat.length > 1) {
                        cat = cat.dropLast(1)
                    }
                    cat = "$cat..."
                }
                canvas.drawText(cat, 300f, y + 13f, paint)

                // Type
                val typeLabel = if (rec.type == "income") "Pemasukan" else "Pengeluaran"
                canvas.drawText(typeLabel, 390f, y + 13f, paint)

                // Amount
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = if (rec.type == "income") {
                    android.graphics.Color.parseColor("#166534")
                } else {
                    android.graphics.Color.parseColor("#991B1B")
                }
                val amtStr = FormatUtils.formatRupiah(rec.amount)
                val amtW = paint.measureText(amtStr)
                canvas.drawText(amtStr, 550f - amtW, y + 13f, paint)

                y += 20f
            }
        }

        pdfDocument.finishPage(currentPage)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    fun addRecurringTransaction(
        description: String,
        amount: Double,
        type: String,
        category: String,
        dayOfMonth: Int,
        notes: String
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val recurring = RecurringTransaction(
                    description = description,
                    amount = amount,
                    type = type,
                    category = category,
                    dayOfMonth = dayOfMonth,
                    notes = notes,
                    lastRunDate = null,
                    isActive = true
                )
                recurringDao.insertRecurring(recurring)
                
                checkAndProcessRecurringTransactions()
                
                uiEvent.emit(UiEvent.ShowToast("Transaksi berulang berhasil ditambahkan!"))
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowToast("Gagal menambahkan transaksi berulang: ${e.message}"))
            }
        }
    }

    fun deleteRecurringTransaction(item: RecurringTransaction) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                recurringDao.deleteRecurring(item)
                uiEvent.emit(UiEvent.ShowToast("Transaksi berulang berhasil dihapus!"))
            } catch (e: Exception) {
                e.printStackTrace()
                uiEvent.emit(UiEvent.ShowToast("Gagal menghapus transaksi berulang: ${e.message}"))
            }
        }
    }

    fun toggleRecurringTransactionActive(item: RecurringTransaction) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val updated = item.copy(isActive = !item.isActive)
                recurringDao.updateRecurring(updated)
                
                if (updated.isActive) {
                    checkAndProcessRecurringTransactions()
                }
                
                uiEvent.emit(UiEvent.ShowToast("Status transaksi berulang berhasil diperbarui!"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkAndProcessRecurringTransactions() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activeList = recurringDao.getActiveRecurring()
                val now = java.time.LocalDate.now()
                val zoneId = java.time.ZoneId.systemDefault()
                var processedAny = false
                
                for (recurring in activeList) {
                    val startYM = if (recurring.lastRunDate != null) {
                        val lastLocalDate = java.time.Instant.ofEpochMilli(recurring.lastRunDate)
                            .atZone(zoneId)
                            .toLocalDate()
                        java.time.YearMonth.of(lastLocalDate.year, lastLocalDate.monthValue)
                    } else {
                        java.time.YearMonth.of(now.year, now.monthValue).minusMonths(1)
                    }

                    val endYM = java.time.YearMonth.of(now.year, now.monthValue)
                    var currentYM = startYM
                    var lastSelectedRunMs = recurring.lastRunDate

                    while (!currentYM.isAfter(endYM)) {
                        val targetDay = minOf(recurring.dayOfMonth, currentYM.lengthOfMonth())
                        val targetDate = currentYM.atDay(targetDay)
                        
                        val targetTs = targetDate.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
                        
                        val isPastOrToday = !targetDate.isAfter(now)
                        val isNewerThanLastRun = lastSelectedRunMs == null || targetTs > lastSelectedRunMs

                        if (isPastOrToday && isNewerThanLastRun) {
                            val record = FinancialRecord(
                                description = recurring.description,
                                amount = recurring.amount,
                                type = recurring.type,
                                category = recurring.category,
                                date = targetTs,
                                notes = recurring.notes.ifBlank { "Diproses otomatis dari transaksi berulang" }
                            )
                            repository.insert(record)
                            
                            lastSelectedRunMs = targetTs
                            processedAny = true
                        }
                        
                        currentYM = currentYM.plusMonths(1)
                    }
                    
                    if (lastSelectedRunMs != recurring.lastRunDate) {
                        val updated = recurring.copy(lastRunDate = lastSelectedRunMs)
                        recurringDao.updateRecurring(updated)
                    }
                }
                
                if (processedAny) {
                    uiEvent.emit(UiEvent.ShowToast("Transaksi berulang baru berhasil dimasukkan otomatis!"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = FinancialRecordRepository(database.financialRecordDao())
                return FinancialTrackerViewModel(application, repository) as T
            }
        }
    }
}
