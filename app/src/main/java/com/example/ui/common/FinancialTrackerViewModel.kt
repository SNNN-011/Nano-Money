package com.example.ui.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettingsRepository
import com.example.data.Category
import com.example.data.CategoryBudgetRepository
import com.example.data.CategoryCascadeService
import com.example.data.CategoryRepository
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import com.example.data.ImportMode
import com.example.data.RecurringTransaction
import com.example.data.RecurringTransactionRepository
import com.example.data.SyncableSettings
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.ui.config.FormatUtils

/** Saldo kumulatif sepanjang riwayat; tidak dibatasi bulan. */
data class DashboardTotals(val income: Long, val expense: Long) {
    val balance: Long get() = income - expense
}

/** Pendapatan dan pengeluaran dalam satu bulan; mengikuti bulan yang sedang dipilih. */
data class MonthTotals(val income: Long, val expense: Long)

/**
 * Menjumlah pendapatan dan pengeluaran dalam rentang tanggal tertentu.
 *
 * Fungsi murni supaya bisa diuji. Dipakai untuk angka PENDAPATAN dan PENGELUARAN di
 * header, yang sengaja mengikuti bulan terpilih, bukan saldo kumulatif.
 */
/**
 * Preferensi tampilan saldo tersembunyi.
 *
 * Murni lokal, tidak pernah ikut sinkron ke Firestore.
 *
 * Disimpan di SharedPreferences biasa, bukan encrypted prefs. Ini bukan rahasia,
 * dan `SecurePrefsHelper` punya jalur recovery yang menghapus seluruh file prefs
 * bila Keystore bermasalah — yang berarti preferensi tampilan ikut hilang padahal
 * tidak ada hubungannya dengan PIN.
 */
const val PREF_SHOW_BALANCE = "show_balance"

private const val UI_PREFS_NAME = "ui_prefs"

internal fun computeMonthTotals(
    records: List<FinancialRecord>,
    startMs: Long,
    endMs: Long
): MonthTotals {
    var income = 0L
    var expense = 0L
    for (record in records) {
        if (record.date < startMs || record.date > endMs) continue
        if (record.type == "income") income += record.amount else if (record.type == "expense") expense += record.amount
    }
    return MonthTotals(income, expense)
}

/**
 * Menjumlah seluruh transaksi tanpa batas tanggal.
 *
 * Fungsi murni supaya bisa diuji. Pernah versi sebelumnya ikut difilter per bulan
 * terpilih, sehingga "saldo total" ikut berubah kalau user memutar bulan.
 */
internal fun computeDashboardTotals(records: List<FinancialRecord>): DashboardTotals =
    DashboardTotals(
        income = records.filter { it.type == "income" }.sumOf { it.amount },
        expense = records.filter { it.type == "expense" }.sumOf { it.amount }
    )

class FinancialTrackerViewModel(
    application: Application,
    private val repository: FinancialRecordRepository
) : AndroidViewModel(application) {

    // ---------------------------------------------------------------
    // Filter. Satu objek FilterState adalah satu-satunya sumber
    // kebenaran, supaya menambah dimensi filter di kemudian hari tidak
    // membuat cabang kondisi baru di banyak tempat.
    // ---------------------------------------------------------------
    data class FilterState(
        val type: String = "Semua",           // "Semua", "Pendapatan", "Pengeluaran"
        val searchQuery: String = "",
        val sortByNewest: Boolean = true
    ) {
        val isDefault: Boolean
            get() = type == "Semua" && searchQuery.isEmpty() && sortByNewest
    }

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // Dibaca UI yang hanya butuh satu bagian saja.
    val filterType: StateFlow<String> = _filterState
        .map { it.type }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Semua")
    val sortByNewest: StateFlow<Boolean> = _filterState
        .map { it.sortByNewest }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val searchQuery: StateFlow<String> = _filterState
        .map { it.searchQuery }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun updateFilterType(type: String) {
        _filterState.value = _filterState.value.copy(type = type)
    }

    fun toggleSortByNewest() {
        _filterState.value = _filterState.value.copy(sortByNewest = !_filterState.value.sortByNewest)
    }

    /**
     * Reset semua filter. Dipanggil saat berpindah tab supaya Beranda tidak
     * dibuka dengan tampilan sisa tab sebelumnya.
     */
    fun resetFilters() {
        _filterState.value = FilterState()
    }

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    // Raw records from database
    val allRecords: StateFlow<List<FinancialRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val recurringRepository =
        com.example.data.RecurringTransactionRepository(AppDatabase.getDatabase(application).recurringTransactionDao())

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = recurringRepository.allRecurring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined filtered & sorted records
    val filteredRecords: StateFlow<List<FinancialRecord>> = combine(
        allRecords, _filterState
    ) { records, f ->
        val queryLower = f.searchQuery.trim().lowercase()
        val searched = if (queryLower.isEmpty()) records else records.filter {
            it.description.lowercase().contains(queryLower) ||
            it.category.lowercase().contains(queryLower) ||
            it.notes.lowercase().contains(queryLower)
        }
        val byType = when (f.type) {
            "Pendapatan" -> searched.filter { it.type == "income" }
            "Pengeluaran" -> searched.filter { it.type == "expense" }
            else -> searched
        }
        // Tanggal dari date picker dinormalkan ke jam 12:00:00, jadi semua transaksi
        // di hari yang sama punya timestamp identik. Tanpa pemutus seri, sortedBy
        // hanya membalik urutan asli dan toggle Latest/Terlama kelihatan tidak
        // mengubah apa pun. Tie-break lewat id membuat urutannya benar-benar berubah.
        if (f.sortByNewest) {
            byType.sortedWith(compareByDescending<FinancialRecord> { it.date }.thenByDescending { it.id })
        } else {
            byType.sortedWith(compareBy<FinancialRecord> { it.date }.thenBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Form inputs state
    // L11: satu sumber kebenaran sebagai StateFlow supaya header "EDIT TRANSAKSI"
    // dan tombol simpan ikut recompose saat mode edit berubah.
    private val _editingRecordId = MutableStateFlow<Int?>(null)
    val editingRecordId: StateFlow<Int?> = _editingRecordId.asStateFlow()

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

    private val prefs = com.example.util.SecurePrefsHelper.getEncryptedPrefs(application, "financial_tracker_prefs")

    private val appDb = AppDatabase.getDatabase(application)

    /** Sumber tunggal kategori, anggaran, dan pengaturan (tabel Room, bukan prefs lagi). */
    private val categoryRepository = CategoryRepository(appDb.categoryDao(), prefs)
    private val categoryBudgetRepository = CategoryBudgetRepository(appDb.categoryBudgetDao())
    private val appSettingsRepository = AppSettingsRepository(appDb.appSettingDao())
    private val categoryCascade = CategoryCascadeService(appDb.financialRecordDao())

    val incomeCategoryEntities: StateFlow<List<Category>> =
        categoryRepository.observeByType("income")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategoryEntities: StateFlow<List<Category>> =
        categoryRepository.observeByType("expense")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Nama kategori untuk parser AI & chip form.
    val incomeCategories: StateFlow<List<String>> = incomeCategoryEntities
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<String>> = expenseCategoryEntities
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Peta categoryId -> nama terbaru; tampilan transaksi me-resolve lewat peta ini. */
    val categoryNameById: StateFlow<Map<String, String>> = categoryRepository.nameById
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Jumlah transaksi aktif per nama kategori. Dipakai dialog hapus kategori (H7)
     * untuk menyebut berapa transaksi yang akan dipindah ke "Lainnya".
     */
    val categoryRecordCounts: StateFlow<Map<String, Int>> = allRecords
        .map { records -> records.groupingBy { it.category }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Apakah nominal saldo ditampilkan.
     *
     * Disimpan di prefs, bukan hanya di state: kalau hanya di remember, statusnya
     * kembali ke "terlihat" setiap aplikasi ditutup dan dibuka ulang.
     */
    private val uiPrefs = application.getSharedPreferences(UI_PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val balanceVisibleState = MutableStateFlow(
        uiPrefs.getBoolean(PREF_SHOW_BALANCE, true)
    )
    val isBalanceVisible: StateFlow<Boolean> = balanceVisibleState.asStateFlow()

    fun toggleBalanceVisibility() {
        val next = !balanceVisibleState.value
        balanceVisibleState.value = next
        // commit(), bukan apply(): apply() menunda penulisan ke disk, jadi kalau
        // proses langsung mati prefs belum tentu sudah tersimpan.
        uiPrefs.edit().putBoolean(PREF_SHOW_BALANCE, next).commit()
    }

    val monthlyBudgetLimit = MutableStateFlow(0L)
    val selectedBudgetOffset = MutableStateFlow(0)

    init {
        // Migrasi kategori, anggaran, dan anggaran bulanan dari prefs lama ke Room.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var migrated = CategoryRepository.runIfNeeded(
                    appDb.categoryDao(), appDb.categoryBudgetDao(), prefs
                )
                migrated = AppSettingsRepository.runIfNeeded(appDb.appSettingDao(), prefs) || migrated

                // Transaksi hasil migrasi DB lama belum punya categoryId, padahal
                // kategorinya sudah ada. Diisi sekarang supaya rename/hapus kategori
                // juga berefek ke transaksi lama.
                val recordRepo = FinancialRecordRepository.create(appDb)
                val backfilled = recordRepo.backfillCategoryIds() > 0

                if (migrated || backfilled) pushPendingToCloud()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Anggaran bulanan mengikuti tabel app_settings (allow-list), bukan prefs.
        appSettingsRepository.observe(SyncableSettings.MONTHLY_BUDGET_LIMIT)
            .onEach { setting -> setting?.valueLong?.let { monthlyBudgetLimit.value = it } }
            .launchIn(viewModelScope)
    }

    // Batas anggaran per kategori, dikunci dengan categoryId (bukan nama).
    val categoryBudgets: StateFlow<Map<String, Long>> = categoryBudgetRepository.observeAll()
        .map { list ->
            list.filter { !it.isDeleted && it.budgetLimit > 0L }.associate { it.categoryId to it.budgetLimit }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Map of category name to current month's expenses for that category
    val categorySpending: StateFlow<Map<String, Long>> = combine(allRecords, selectedBudgetOffset) { records, offset ->
        val (rangeStart, rangeEnd) = getMonthRange(offset)
        records.filter { it.type == "expense" && it.date in rangeStart..rangeEnd }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateCategoryBudget(categoryId: String, limit: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            categoryBudgetRepository.setLimit(categoryId, limit)
            pushPendingToCloud()
        }
    }

    fun changeBudgetMonthOffset(offset: Int) {
        selectedBudgetOffset.value = offset
    }

    fun updateMonthlyBudgetLimit(limit: Long) {
        monthlyBudgetLimit.value = limit
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            appSettingsRepository.setLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, limit)
            pushPendingToCloud()
        }
    }

    val monthlySpendingTotal: StateFlow<Long> = selectedBudgetOffset
        .flatMapLatest { offset ->
            val (rangeStart, rangeEnd) = getMonthRange(offset)
            appDb.analysisDao().getTotalExpense(rangeStart, rangeEnd)
        }
        .map { it ?: 0L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // SALDO TOTAL: kumulatif sepanjang riwayat, tidak berubah saat bulan diputar.
    // Ini yang menjelaskan berapa uang yang benar-benar ada sekarang, bukan berapa
    // yang keluar bulan ini saja.
    private val dashboardTotals: StateFlow<DashboardTotals> = combine(
        appDb.analysisDao().getAllTimeTotalIncome(),
        appDb.analysisDao().getAllTimeTotalExpense()
    ) { income, expense -> DashboardTotals(income ?: 0L, expense ?: 0L) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardTotals(0L, 0L))

    val currentBalance: StateFlow<Long> = dashboardTotals
        .map { it.balance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // PENDAPATAN & PENGELUARAN: mengikuti bulan yang sedang dipilih, sama seperti
    // bagian anggaran. Jadi memutar bulan ikut mengubah kedua angka ini, sedangkan
    // SALDO TOTAL di atasnya tetap.
    // M2: agregat bulanan dihitung SQL (AnalysisDao) dan berhenti memuat seluruh tabel.
    private val monthTotals: StateFlow<MonthTotals> = selectedBudgetOffset
        .flatMapLatest { offset ->
            val (rangeStart, rangeEnd) = getMonthRange(offset)
            combine(
                appDb.analysisDao().getTotalIncome(rangeStart, rangeEnd),
                appDb.analysisDao().getTotalExpense(rangeStart, rangeEnd)
            ) { income, expense -> MonthTotals(income ?: 0L, expense ?: 0L) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthTotals(0L, 0L))

    val totalIncome: StateFlow<Long> = monthTotals
        .map { it.income }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalExpense: StateFlow<Long> = monthTotals
        .map { it.expense }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private fun getMonthRange(offset: Int): Pair<Long, Long> {
        val now = java.time.Instant.now()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val target = now.plusMonths(offset.toLong())
        val start = target.withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val end = target.withDayOfMonth(target.lengthOfMonth())
            .plusDays(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - 1
        return start to end
    }

    init {
        // Automatically keep category logic sensible when type changes
        formType.onEach { type ->
            if (type == "income") {
                if (!incomeCategories.value.contains(formCategory.value)) {
                    formCategory.value = incomeCategories.value.firstOrNull() ?: "Gaji"
                }
            } else {
                if (!expenseCategories.value.contains(formCategory.value)) {
                    formCategory.value = expenseCategories.value.firstOrNull() ?: "Makanan & Minuman"
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
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000L, "income", "Gaji", cal.timeInMillis, "Gaji pokok"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 8)
                    sampleData.add(FinancialRecord(0, "Makan Siang", 25000L, "expense", "Makanan & Minuman", cal.timeInMillis, "Bakso"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 5)
                    sampleData.add(FinancialRecord(0, "Beli Buku", 150000L, "expense", "Belanja", cal.timeInMillis, "Buku Android"))
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 3)
                    sampleData.add(FinancialRecord(0, "Transportasi Gojek", 20000L, "expense", "Transportasi", cal.timeInMillis, "Ke kantor"))

                    // --- LAST MONTH (1 Month Ago) ---
                    val lastMonthCal = java.util.Calendar.getInstance()
                    lastMonthCal.add(java.util.Calendar.MONTH, -1)
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 10)
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000L, "income", "Gaji", lastMonthCal.timeInMillis, "Gaji pokok"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 15)
                    sampleData.add(FinancialRecord(0, "Belanja Sepatu Baru", 450000L, "expense", "Belanja", lastMonthCal.timeInMillis, "Sepatu lari"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 18)
                    sampleData.add(FinancialRecord(0, "Makan Malam Bersama", 180000L, "expense", "Makanan & Minuman", lastMonthCal.timeInMillis, "Restoran"))
                    lastMonthCal.set(java.util.Calendar.DAY_OF_MONTH, 22)
                    sampleData.add(FinancialRecord(0, "Langganan Netflix", 54000L, "expense", "Langganan", lastMonthCal.timeInMillis, "Paket Standard"))

                    // --- 2 MONTHS AGO ---
                    val twoMonthsAgoCal = java.util.Calendar.getInstance()
                    twoMonthsAgoCal.add(java.util.Calendar.MONTH, -2)
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 10)
                    sampleData.add(FinancialRecord(0, "Gaji Bulanan", 5000000L, "income", "Gaji", twoMonthsAgoCal.timeInMillis, "Gaji pokok"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 5)
                    sampleData.add(FinancialRecord(0, "Servis Motor", 350000L, "expense", "Transportasi", twoMonthsAgoCal.timeInMillis, "Ganti oli & ban"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 12)
                    sampleData.add(FinancialRecord(0, "Makan Bakso Lapangan", 30000L, "expense", "Makanan & Minuman", twoMonthsAgoCal.timeInMillis, ""))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 20)
                    sampleData.add(FinancialRecord(0, "Tagihan Internet Wifi", 150000L, "expense", "Internet & Pulsa", twoMonthsAgoCal.timeInMillis, "Bulanan"))
                    twoMonthsAgoCal.set(java.util.Calendar.DAY_OF_MONTH, 25)
                    sampleData.add(FinancialRecord(0, "Tiket Bioskop", 65000L, "expense", "Hiburan", twoMonthsAgoCal.timeInMillis, "Nonton film"))

                    repository.insertAll(sampleData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Menambah kategori. Hasil nyata dikembalikan (bukan selalu `true`) supaya
     * dialog bisa menampilkan kegagalan, mis. duplikat beda huruf besar-kecil.
     */
    suspend fun addCategory(type: String, category: String): Boolean {
        val (_, cleanName) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(category.trim())
        val trimmed = cleanName.ifBlank { category.trim() }.trim()
        if (trimmed.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            val added = categoryRepository.add(type, trimmed) != null
            if (added) {
                pushPendingToCloud()
                uiEvent.emit(UiEvent.ShowToast("Kategori \"$trimmed\" ditambahkan."))
            }
            added
        }
    }

    /**
     * Menghapus kategori: soft delete kategori + pindah transaksi (termasuk isi
     * Sampah) + cabut anggaran, semuanya dalam SATU transaksi (H7). Gagal di
     * tengah = rollback penuh; tidak ada transaksi yang menunjuk kategori mati.
     */
    suspend fun deleteCategory(type: String, category: String): Boolean {
        val trimmed = category.trim()
        return try {
            val ok = appDb.withTransaction {
                val existing = categoryRepository.findByName(type, trimmed) ?: return@withTransaction false
                if (!categoryRepository.softDelete(existing.id)) return@withTransaction false

                val fallback = categoryRepository.findByName(type, FALLBACK_CATEGORY_NAME)
                    ?: categoryRepository.add(type, FALLBACK_CATEGORY_NAME)
                if (fallback != null) categoryCascade.applyMove(existing.id, fallback)
                categoryBudgetRepository.setLimit(existing.id, 0L)
                true
            }
            if (ok) {
                if (formCategory.value == trimmed) {
                    val replacement = categoryRepository.getByType(type).firstOrNull()
                    if (replacement != null) formCategory.value = replacement.name
                }
                pushPendingToCloud()
                uiEvent.emit(UiEvent.ShowToast("Kategori \"$trimmed\" dihapus."))
            }
            ok
        } catch (e: Exception) {
            uiEvent.emit(
                UiEvent.ShowAlert(
                    "Gagal Menghapus Kategori",
                    "Kategori tidak dihapus: ${e.localizedMessage ?: "kesalahan database"}"
                )
            )
            false
        }
    }

    /**
     * Mengubah nama kategori. ID kategori tetap sama, jadi anggaran tetap tertaut,
     * dan nama yang tersimpan di transaksi ikut diperbarui.
     */
    suspend fun renameCategory(type: String, currentName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false

        val existing = categoryRepository.findByName(type, currentName.trim()) ?: return false
        val renamed = categoryRepository.rename(existing.id, trimmed)
        if (renamed) {
            categoryCascade.applyRename(existing.id, trimmed)
            if (formCategory.value == currentName.trim()) formCategory.value = trimmed
            pushPendingToCloud()
        }
        return renamed
    }

    fun setEditingRecord(record: FinancialRecord?) {
        if (record != null) {
            _editingRecordId.value = record.id
            formDescription.value = record.description
            val rawAmt = record.amount.toString()
            formAmount.value = FormatUtils.formatInputNumber(rawAmt)
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
        _editingRecordId.value = null
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

        val amtStr = formAmount.value.trim().replace(".", "")
        val amt = amtStr.toLongOrNull()
        if (amtStr.isEmpty()) {
            amountError.value = "Jumlah tidak boleh kosong"
            isValid = false
        } else if (amt == null || amt <= 0L) {
            amountError.value = "Jumlah harus berupa angka positif"
            isValid = false
        } else if (amt > FinancialRecordRepository.MAX_AMOUNT) {
            // M12: batas atas eksplisit, bukan hanya toLongOrNull (overflow).
            amountError.value = "Jumlah melebihi batas maksimum"
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

        // Ambil sekali supaya mode edit tidak berubah di tengah proses simpan.
        val editingId = _editingRecordId.value
        val record = FinancialRecord(
            id = editingId ?: 0,
            description = desc,
            amount = amt ?: 0L,
            type = formType.value,
            category = formCategory.value,
            date = formDate.value,
            notes = formNotes.value.trim()
        )

        viewModelScope.launch {
            try {
                if (editingId != null) {
                    repository.update(record)
                    pushPendingToCloud()
                    com.example.util.TelemetryHelper.trackTransactionAction("edit", record.type, record.category, record.amount)
                    uiEvent.emit(UiEvent.ShowToast("Transaksi berhasil diperbarui!"))
                } else {
                    repository.insert(record)
                    pushPendingToCloud()
                    com.example.util.TelemetryHelper.trackTransactionAction("add", record.type, record.category, record.amount)
                    uiEvent.emit(UiEvent.ShowToast("Transaksi baru berhasil disimpan!"))
                }
                resetForm()
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Menyimpan", "Gagal menyimpan transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
        return true
    }

    /** Dorong antrean PENDING ke Firestore setelah tulis lokal (diamkan bila gagal). */
    private suspend fun pushPendingToCloud() {
        if (!com.example.util.FirebaseSyncHelper.isUserSignedIn()) return
        runCatching { com.example.util.FirebaseSyncHelper.pushPending(getApplication()) }
    }

    fun deleteRecord(record: FinancialRecord) {
        viewModelScope.launch {
            try {
                repository.delete(record)
                pushPendingToCloud()
                com.example.util.TelemetryHelper.trackTransactionAction("delete", record.type, record.category, record.amount)
                uiEvent.emit(UiEvent.ShowToast("Transaksi berhasil dihapus!"))
                if (_editingRecordId.value == record.id) {
                    resetForm()
                }
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Menghapus", "Gagal menghapus transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
    }

    // ---- Sampah (trash) ----
    // Penghapusan bersifat soft delete, jadi record yang "dihapus" masih
    // bisa dipulihkan dari sini.
    val deletedRecords: StateFlow<List<FinancialRecord>> = repository.getDeletedRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreFromTrash(record: FinancialRecord) {
        viewModelScope.launch {
            try {
                repository.restore(record)
                pushPendingToCloud()
                uiEvent.emit(UiEvent.ShowToast("${record.description} berhasil dipulihkan!"))
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Memulihkan", "Gagal memulihkan transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
    }

    fun restoreAllFromTrash() {
        viewModelScope.launch {
            try {
                val items = repository.getDeletedRecords().first()
                if (items.isEmpty()) {
                    uiEvent.emit(UiEvent.ShowToast("Sampah sudah kosong."))
                    return@launch
                }
                repository.restore(items)
                pushPendingToCloud()
                uiEvent.emit(UiEvent.ShowToast("${items.size} transaksi berhasil dipulihkan!"))
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Memulihkan", "Gagal memulihkan transaksi: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                // ID harus dikumpulkan sebelum baris dihapus, karena setelah purge
                // tidak ada lagi yang bisa ditanyakan ke database.
                val trashed = repository.getDeletedRecords().first()
                if (trashed.isEmpty()) {
                    uiEvent.emit(UiEvent.ShowToast("Sampah sudah kosong."))
                    return@launch
                }
                repository.emptyTrash()
                // Cloud harus ikut dihapus permanen. Kalau tidak, dokumennya
                // masih tertinggal di Firestore dan sync saat aplikasi dibuka
                // menariknya kembali, sehingga Sampah terisi lagi.
                com.example.util.FirebaseSyncHelper.purgeRecordsFromFirestore(trashed)
                uiEvent.emit(UiEvent.ShowToast("Sampah dikosongkan."))
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Gagal Mengosongkan", "Gagal mengosongkan sampah: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
    }

    /**
     * Hapus semua transaksi aktif: dipindahkan ke Sampah, bukan dihapus
     * permanen, sehingga masih bisa dipulihkan dari tab Simpan.
     */
    fun deleteAllRecords() {
        viewModelScope.launch {
            try {
                val items = repository.allRecords.first()
                if (items.isEmpty()) {
                    uiEvent.emit(UiEvent.ShowToast("Belum ada transaksi untuk dihapus."))
                    return@launch
                }
                repository.moveAllActiveToTrash()
                pushPendingToCloud()
                com.example.util.TelemetryHelper.trackBackupAction("delete_all", true, "${items.size} transaksi dipindahkan ke Sampah")
                uiEvent.emit(UiEvent.ShowToast("${items.size} transaksi dipindahkan ke Sampah."))
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

    /** Kategori yang muncul di data impor otomatis ditambahkan ke tabel kategori. */
    private suspend fun importCategoriesFromRecords(records: List<FinancialRecord>) {
        var modified = false
        // M20: muat kategori sekali per tipe (hindari satu query per baris impor).
        val knownByType = HashMap<String, MutableSet<String>>()
        for (type in listOf("income", "expense")) {
            knownByType[type] = categoryRepository.getByType(type)
                .mapTo(HashSet()) { it.name.trim().lowercase() }
        }
        for (record in records) {
            val (_, cat) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(record.category.trim())
            val cleanCat = cat.ifBlank { record.category.trim() }.trim()
            if (cleanCat.isEmpty()) continue
            val known = knownByType[record.type] ?: continue

            if (known.add(cleanCat.lowercase())) {
                if (categoryRepository.add(record.type, cleanCat) != null) modified = true
            }
        }
        if (modified) pushPendingToCloud()
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

                val amount = obj.optLong("amount", -1L)
                if (amount <= 0) throw IllegalArgumentException("Jumlah baris ${i+1} harus angka positif.")

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

    /**
     * Escape nilai CSV + netralkan formula spreadsheet (M9), sama seperti sisi impor:
     * nilai berawalan `=`, `+`, `-`, `@`, tab, CR, LF diberi awalan apostrof supaya
     * tidak dieksekusi sebagai formula saat file dibuka Excel/Sheets.
     */
    private fun escapeCsvField(field: String): String {
        val neutralized = if (field.isNotEmpty() && field[0] in setOf('=', '+', '-', '@', '\t', '\r', '\n')) {
            "'" + field
        } else {
            field
        }
        return "\"" + neutralized.replace("\"", "\"\"") + "\""
    }

    // Export Excel/CSV logic
    fun getExportCsvString(selectedMonths: List<YearMonth>): String {
        val sb = java.lang.StringBuilder()
        // BOM UTF-8 supaya Excel membaca file sebagai UTF-8, diikuti baris header.
        sb.append('\uFEFF')
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
            sb.append(escapeCsvField(record.description)).append(",")
            sb.append(record.amount).append(",")
            val typeLabel = if (record.type == "income") "Pemasukan" else "Pengeluaran"
            sb.append(escapeCsvField(typeLabel)).append(",")
            sb.append(escapeCsvField(record.category)).append(",")
            val formattedDate = sdf.format(java.util.Date(record.date))
            sb.append(escapeCsvField(formattedDate)).append(",")
            sb.append(escapeCsvField(record.notes)).append("\n")
        }
        return sb.toString()
    }

    /**
     * Impor baris dari file Excel/CSV.
     *
     * [mode] menentukan nasib data lama: [ImportMode.MERGE] hanya menambahkan baris
     * baru, sedangkan [ImportMode.REPLACE] memindahkan semua transaksi aktif ke
     * Sampah (masih bisa dipulihkan) lalu memasukkan baris impor. Tidak ada jalur
     * yang menghapus data secara permanen.
     *
     * Parsing dan tulis database dijalankan di [Dispatchers.IO] karena file bisa besar.
     * Hasil sukses/gagal dilaporkan lewat uiEvent, bukan lewat nilai balik.
     */
    fun importFromCsvString(csvStr: String, mode: ImportMode): Boolean {
        viewModelScope.launch {
            val parsedList = try {
                withContext(Dispatchers.IO) {
                    com.example.domain.CsvImportUseCase().parseCsvString(csvStr)
                }
            } catch (e: Exception) {
                uiEvent.emit(
                    UiEvent.ShowAlert(
                        title = "Impor Gagal",
                        message = "Gagal memproses file Excel/CSV: ${e.localizedMessage ?: "Format tidak valid"}"
                    )
                )
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    if (mode == ImportMode.REPLACE) {
                        repository.replaceAllSoft(parsedList)
                    } else {
                        repository.mergeAll(parsedList)
                    }
                }
                importCategoriesFromRecords(parsedList)
                pushPendingToCloud()
                uiEvent.emit(
                    UiEvent.ShowToast(
                        if (mode == ImportMode.REPLACE) {
                            "Berhasil mengganti data aktif dengan ${parsedList.size} transaksi dari Excel/CSV. Data lama dipindah ke Sampah dan masih bisa dipulihkan."
                        } else {
                            "Berhasil menambahkan ${parsedList.size} transaksi dari Excel/CSV ke data yang ada."
                        }
                    )
                )
            } catch (e: Exception) {
                uiEvent.emit(UiEvent.ShowAlert("Impor Gagal", "Gagal menyimpan data ke database: ${e.localizedMessage ?: "kesalahan database"}"))
            }
        }
        // Proses berjalan asinkron; hasilnya hanya dilaporkan lewat uiEvent.
        return true
    }

    // Generate Monthly PDF Report
    fun generatePdfReport(context: Context, outputStream: OutputStream, selectedMonths: List<YearMonth>) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1

        val sortedMonths = if (selectedMonths.isEmpty()) {
            listOf(YearMonth.now())
        } else {
            selectedMonths.sorted()
        }

        fun getMonthNameIndo(monthVal: Int): String {
            return when (monthVal) {
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
        }

        for (ym in sortedMonths) {
            // Filter and compute records for this specific month
            val monthRecords = allRecords.value.filter { record ->
                val date = Instant.ofEpochMilli(record.date)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                date.year == ym.year && date.monthValue == ym.monthValue
            }.sortedBy { it.date }

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
                val mLabel = "${getMonthNameIndo(ym.monthValue)} ${ym.year}"
                canvas.drawText("NANO MONEY - LAPORAN KEUANGAN [$mLabel]", 40f, 35f, hPaint)
                
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

            drawPageHeader(canvas, pageNumber)

            val paint = Paint().apply { isAntiAlias = true }

            // Logo and Title Header
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

            paint.color = android.graphics.Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText("Laporan Keuangan Bulanan", 40f, y, paint)
            
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = android.graphics.Color.parseColor("#64748B")
            val periodLabel = "Periode: ${getMonthNameIndo(ym.monthValue)} ${ym.year}"
            canvas.drawText(periodLabel, 40f, y + 14f, paint)

            y += 24f
            paint.color = android.graphics.Color.parseColor("#E2E8F0")
            canvas.drawLine(40f, y, 555f, y, paint)

            var totalIn = 0L
            var totalOut = 0L
            monthRecords.forEach { record ->
                if (record.type == "income") totalIn += record.amount else totalOut += record.amount
            }
            val netBalance = totalIn - totalOut

            y += 15f

            // Draw Summary Box background
            paint.color = android.graphics.Color.parseColor("#F8FAFC")
            canvas.drawRect(40f, y, 555f, y + 55f, paint)

            // Summary Box border
            paint.color = android.graphics.Color.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(40f, y, 555f, y + 55f, paint)
            paint.style = Paint.Style.FILL

            // Total In
            paint.color = android.graphics.Color.parseColor("#64748B")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("TOTAL PEMASUKAN", 60f, y + 18f, paint)
            paint.color = android.graphics.Color.parseColor("#15803D")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(FormatUtils.formatRupiah(totalIn), 60f, y + 35f, paint)

            // Total Out
            paint.color = android.graphics.Color.parseColor("#64748B")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("TOTAL PENGELUARAN", 230f, y + 18f, paint)
            paint.color = android.graphics.Color.parseColor("#B91C1C")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(FormatUtils.formatRupiah(totalOut), 230f, y + 35f, paint)

            // Net Balance
            paint.color = android.graphics.Color.parseColor("#64748B")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("SALDO BERSIH", 400f, y + 18f, paint)
            paint.color = if (netBalance >= 0) android.graphics.Color.parseColor("#0F172A") else android.graphics.Color.parseColor("#B91C1C")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(FormatUtils.formatRupiah(netBalance), 400f, y + 35f, paint)

            y += 70f

            // --- SECTION BANNER: ANALISIS DAN ANGGARAN & KATEGORI ---
            paint.color = android.graphics.Color.parseColor("#0F172A")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("ANALISIS GRAFIK ARUS KAS, ANGGARAN & KATEGORI", 40f, y, paint)

            y += 8f

            // Clean Outer analytics card
            paint.color = android.graphics.Color.parseColor("#F1F5F9")
            canvas.drawRect(40f, y, 555f, y + 140f, paint)

            paint.color = android.graphics.Color.parseColor("#CBD5E1")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(40f, y, 555f, y + 140f, paint)
            paint.style = Paint.Style.FILL

            // Left Column: cashflow and budget bars
            paint.color = android.graphics.Color.parseColor("#1E293B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            canvas.drawText("Perbandingan Pemasukan vs Pengeluaran:", 55f, y + 18f, paint)

            val totalFlow = totalIn + totalOut
            val inRatio = if (totalFlow > 0) (totalIn.toDouble() / totalFlow).toFloat() else 0.5f
            val outRatio = if (totalFlow > 0) (totalOut.toDouble() / totalFlow).toFloat() else 0.5f

            val barY = y + 25f
            paint.color = android.graphics.Color.parseColor("#10B981") // Income
            canvas.drawRect(55f, barY, 55f + 220f * inRatio, barY + 8f, paint)
            
            paint.color = android.graphics.Color.parseColor("#EF4444") // Expense
            canvas.drawRect(55f + 220f * inRatio, barY, 275f, barY + 8f, paint)

            paint.color = android.graphics.Color.parseColor("#047857")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7f
            canvas.drawText("Pemasukan: ${String.format("%.0f%%", inRatio * 100)}", 55f, barY + 16f, paint)
            
            paint.color = android.graphics.Color.parseColor("#B91C1C")
            val expRatioLabel = "Pengeluaran: ${String.format("%.0f%%", outRatio * 100)}"
            val expRatioWidth = paint.measureText(expRatioLabel)
            canvas.drawText(expRatioLabel, 275f - expRatioWidth, barY + 16f, paint)

            // Budget limits underneath
            val activeLimit = monthlyBudgetLimit.value
            paint.color = android.graphics.Color.parseColor("#1E293B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            canvas.drawText("Laporan Realisasi Anggaran:", 55f, y + 62f, paint)

            val budgetTextY = y + 71f
            if (activeLimit <= 0L) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.color = android.graphics.Color.parseColor("#64748B")
                paint.textSize = 7f
                canvas.drawText("Batas anggaran bulanan belum diatur.", 55f, budgetTextY, paint)
                canvas.drawText("Silakan atur di Dashboard.", 55f, budgetTextY + 10f, paint)
            } else {
                val budgetRatio = (totalOut.toDouble() / activeLimit).coerceAtMost(1.0).toFloat()
                val overBudget = totalOut > activeLimit

                paint.color = android.graphics.Color.parseColor("#E2E8F0") 
                canvas.drawRect(55f, budgetTextY, 275f, budgetTextY + 8f, paint)

                val fillColor = if (overBudget) "#DC2626" else "#4F46E5"
                paint.color = android.graphics.Color.parseColor(fillColor)
                canvas.drawRect(55f, budgetTextY, 55f + 220f * budgetRatio, budgetTextY + 8f, paint)

                paint.color = android.graphics.Color.parseColor("#334155")
                paint.textSize = 6.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Limit: ${FormatUtils.formatRupiah(activeLimit)}", 55f, budgetTextY + 16f, paint)
                canvas.drawText("Terpakai: ${FormatUtils.formatRupiah(totalOut)}", 55f, budgetTextY + 23f, paint)

                val remainingBudget = activeLimit - totalOut
                if (overBudget) {
                    paint.color = android.graphics.Color.parseColor("#DC2626")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 6.5f
                    canvas.drawText("Overbudget!", 55f, budgetTextY + 31f, paint)
                } else {
                    paint.color = android.graphics.Color.parseColor("#15803D")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 6.5f
                    canvas.drawText("Sisa: ${FormatUtils.formatRupiah(remainingBudget)}", 55f, budgetTextY + 31f, paint)
                }
            }

            // Right Column: Category breakdown Pie Chart
            val expenseRecords = monthRecords.filter { it.type == "expense" }
            val totalExpense = expenseRecords.sumOf { it.amount }

            if (totalExpense > 0L) {
                paint.color = android.graphics.Color.parseColor("#1E293B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8f
                canvas.drawText("Analisis Pengeluaran Kategori:", 310f, y + 18f, paint)

                val categoryStats = expenseRecords.groupBy { it.category }
                    .map { (cat, recs) -> cat to recs.sumOf { it.amount } }
                    .sortedByDescending { it.second }

                val topSlices = mutableListOf<Pair<String, Long>>()
                var otherSum = 0L
                categoryStats.forEachIndexed { sIdx, pair ->
                    if (sIdx < 4) {
                        topSlices.add(pair)
                    } else {
                        otherSum += pair.second
                    }
                }
                if (otherSum > 0L) {
                    topSlices.add("Lainnya" to otherSum)
                }

                val sliceColors = listOf(
                    "#6366F1", // Indigo
                    "#10B981", // Emerald
                    "#F59E0B", // Amber
                    "#EC4899", // Pink
                    "#8B5CF6", // Violet
                    "#64748B"  // Slate for other
                )

                var currentAngle = -90f
                val pieRect = android.graphics.RectF(310f, y + 30f, 390f, y + 110f)

                topSlices.forEachIndexed { sIdx, slice ->
                    val ratio = (slice.second.toDouble() / totalExpense).toFloat()
                    val sweepAngle = ratio * 360f
                    val colorHex = if (slice.first == "Lainnya") "#64748B" else sliceColors[sIdx % sliceColors.size]
                    
                    paint.color = android.graphics.Color.parseColor(colorHex)
                    paint.style = Paint.Style.FILL
                    canvas.drawArc(pieRect, currentAngle, sweepAngle, true, paint)

                    paint.color = android.graphics.Color.WHITE
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas.drawArc(pieRect, currentAngle, sweepAngle, true, paint)
                    paint.style = Paint.Style.FILL

                    currentAngle += sweepAngle
                }

                // Legend
                var legendY = y + 34f
                topSlices.forEachIndexed { sIdx, slice ->
                    val ratio = slice.second.toDouble() / totalExpense
                    val colorHex = if (slice.first == "Lainnya") "#64748B" else sliceColors[sIdx % sliceColors.size]

                    paint.color = android.graphics.Color.parseColor(colorHex)
                    canvas.drawRect(405f, legendY - 5f, 411f, legendY + 1f, paint)

                    paint.color = android.graphics.Color.parseColor("#334155")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 6.5f

                    var catLabel = slice.first
                    val pctStr = String.format("%.0f%%", ratio * 100)
                    var fullLabel = "$catLabel ($pctStr)"
                    if (paint.measureText(fullLabel) > 115f) {
                        while (paint.measureText("$catLabel.. ($pctStr)") > 110f && catLabel.length > 1) {
                            catLabel = catLabel.dropLast(1)
                        }
                        fullLabel = "$catLabel.. ($pctStr)"
                    }
                    canvas.drawText(fullLabel, 416f, legendY, paint)

                    paint.color = android.graphics.Color.parseColor("#64748B")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 5.5f
                    canvas.drawText(FormatUtils.formatRupiah(slice.second), 416f, legendY + 6f, paint)

                    legendY += 15f
                }
            } else {
                paint.color = android.graphics.Color.parseColor("#1E293B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8f
                canvas.drawText("Analisis Pengeluaran Kategori:", 310f, y + 18f, paint)

                paint.color = android.graphics.Color.parseColor("#CBD5E1")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawCircle(350f, y + 70f, 25f, paint)
                paint.style = Paint.Style.FILL

                paint.color = android.graphics.Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 7f
                canvas.drawText("Tidak ada pengeluaran", 390f, y + 68f, paint)
                canvas.drawText("pada bulan ini.", 390f, y + 76f, paint)
            }

            y += 155f

            val activeCatBudgets = categoryBudgets.value.filter { it.value > 0L }
            if (activeCatBudgets.isNotEmpty()) {
                checkPageBreak(30f + activeCatBudgets.size * 14f)
                paint.color = android.graphics.Color.parseColor("#0F172A")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("ANGGARAN KATEGORI DETAIL", 40f, y, paint)
                
                y += 10f
                
                paint.color = android.graphics.Color.parseColor("#475569")
                canvas.drawRect(40f, y, 555f, y + 16f, paint)
                
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Kategori", 45f, y + 11f, paint)
                canvas.drawText("Terpakai", 200f, y + 11f, paint)
                canvas.drawText("Limit Anggaran", 310f, y + 11f, paint)
                canvas.drawText("Status / Sisa", 430f, y + 11f, paint)
                
                y += 16f
                
                activeCatBudgets.forEach { (cat, limit) ->
                    val spent = categorySpending.value[cat] ?: 0L
                    val isOver = spent > limit
                    val remaining = limit - spent
                    
                    paint.color = android.graphics.Color.parseColor("#F8FAFC")
                    canvas.drawRect(40f, y, 555f, y + 14f, paint)
                    
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    
                    paint.color = android.graphics.Color.parseColor("#0F172A")
                    canvas.drawText(cat, 45f, y + 10f, paint)
                    canvas.drawText(FormatUtils.formatRupiah(spent), 200f, y + 10f, paint)
                    canvas.drawText(FormatUtils.formatRupiah(limit), 310f, y + 10f, paint)
                    
                    if (isOver) {
                        paint.color = android.graphics.Color.parseColor("#B91C1C")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("Overbudget (${FormatUtils.formatRupiah(spent - limit)})", 430f, y + 10f, paint)
                    } else {
                        paint.color = android.graphics.Color.parseColor("#15803D")
                        canvas.drawText("Sisa: ${FormatUtils.formatRupiah(remaining)}", 430f, y + 10f, paint)
                    }
                    
                    paint.color = android.graphics.Color.parseColor("#E2E8F0")
                    canvas.drawLine(40f, y + 14f, 555f, y + 14f, paint)
                    
                    y += 14f
                }
                y += 20f
            }

            // Table title
            checkPageBreak(40f)
            paint.color = android.graphics.Color.parseColor("#0F172A")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DAFTAR TRANSAKSI DETAIL", 40f, y, paint)

            y += 10f

            // Drawing Table Headers
            paint.color = android.graphics.Color.parseColor("#334155")
            canvas.drawRect(40f, y, 555f, y + 18f, paint)

            paint.color = android.graphics.Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("No", 45f, y + 12f, paint)
            canvas.drawText("Tanggal", 75f, y + 12f, paint)
            canvas.drawText("Deskripsi", 145f, y + 12f, paint)
            canvas.drawText("Kategori", 300f, y + 12f, paint)
            canvas.drawText("Tipe", 390f, y + 12f, paint)
            val amountTitleWidth = paint.measureText("Jumlah")
            canvas.drawText("Jumlah", 550f - amountTitleWidth, y + 12f, paint)

            y += 18f

            if (monthRecords.isEmpty()) {
                checkPageBreak(30f)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 9.5f
                paint.color = android.graphics.Color.parseColor("#64748B")
                canvas.drawText("Tidak ada transaksi untuk bulan yang dipilih.", 45f, y + 14f, paint)
            } else {
                monthRecords.forEachIndexed { idx, rec ->
                    checkPageBreak(22f)

                    // Alternating rows bg
                    if (idx % 2 == 1) {
                        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#F8FAFC") }
                        canvas.drawRect(40f, y, 555f, y + 18f, bgPaint)
                    }

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 8f
                    paint.color = android.graphics.Color.parseColor("#334155")

                    // Index
                    canvas.drawText((idx + 1).toString(), 45f, y + 12f, paint)

                    // Date
                    canvas.drawText(FormatUtils.formatDate(rec.date), 75f, y + 12f, paint)

                    // Description
                    var desc = rec.description
                    if (paint.measureText(desc) > 145f) {
                        while (paint.measureText("$desc...") > 140f && desc.length > 1) {
                            desc = desc.dropLast(1)
                        }
                        desc = "$desc..."
                    }
                    canvas.drawText(desc, 145f, y + 12f, paint)

                    // Category
                    var cat = rec.category
                    if (paint.measureText(cat) > 85f) {
                        while (paint.measureText("$cat...") > 80f && cat.length > 1) {
                            cat = cat.dropLast(1)
                        }
                        cat = "$cat..."
                    }
                    canvas.drawText(cat, 300f, y + 12f, paint)

                    // Type
                    val typeLabel = if (rec.type == "income") "Pemasukan" else "Pengeluaran"
                    canvas.drawText(typeLabel, 390f, y + 12f, paint)

                    // Amount
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = if (rec.type == "income") {
                        android.graphics.Color.parseColor("#166534")
                    } else {
                        android.graphics.Color.parseColor("#991B1B")
                    }
                    val amtStr = FormatUtils.formatRupiah(rec.amount)
                    val amtW = paint.measureText(amtStr)
                    canvas.drawText(amtStr, 550f - amtW, y + 12f, paint)

                    y += 18f
                }
            }

            pdfDocument.finishPage(currentPage)
            pageNumber++
        }

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    fun addRecurringTransaction(
        description: String,
        amount: Long,
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
                recurringRepository.insert(recurring)

                checkAndProcessRecurringTransactions()
                // L8: mutasi recurring juga harus mendorong antrean PENDING ke cloud,
                // sama seperti transaksi biasa.
                pushPendingToCloud()
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
                // Soft delete: aksi hapus ikut tersinkron ke perangkat lain.
                recurringRepository.softDelete(item)
                pushPendingToCloud()
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
                recurringRepository.update(updated)
                pushPendingToCloud()
                
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
                val activeList = recurringRepository.getActive()
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
                            // Kunci deterministik: dua perangkat yang memproses recurring
                            // yang sama pada bulan yang sama harus menghasilkan satu baris.
                            val generatedKey = com.example.data.SyncKeys.recurringGenerated(recurring, currentYM)
                            if (repository.findBySyncKey(generatedKey) == null) {
                                val record = FinancialRecord(
                                    description = recurring.description,
                                    amount = recurring.amount,
                                    type = recurring.type,
                                    category = recurring.category,
                                    date = targetTs,
                                    notes = recurring.notes.ifBlank { "Diproses otomatis dari transaksi berulang" }
                                )
                                repository.insert(record.copy(syncKey = generatedKey))
                            }

                            lastSelectedRunMs = targetTs
                            processedAny = true
                        }
                        
                        currentYM = currentYM.plusMonths(1)
                    }
                    
                    if (lastSelectedRunMs != recurring.lastRunDate) {
                        val updated = recurring.copy(lastRunDate = lastSelectedRunMs)
                        recurringRepository.update(updated)
                    }
                }
                
                if (processedAny) {
                    // Transaksi hasil recurring + perubahan lastRunDate ikut ter-push (L8).
                    pushPendingToCloud()
                    uiEvent.emit(UiEvent.ShowToast("Transaksi berulang baru berhasil dimasukkan otomatis!"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        /** Tujuan transaksi saat kategorinya dihapus. */
        const val FALLBACK_CATEGORY_NAME = "Lainnya"

        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = FinancialRecordRepository.create(database)
                return FinancialTrackerViewModel(application, repository) as T
            }
        }
    }
}

