package com.example.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AnalysisRepository
import com.example.data.AppDatabase
import com.example.data.CategoryAggregation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

import java.text.SimpleDateFormat

data class DailyAggregation(
    val dateString: String,
    val dateMs: Long,
    val totalAmount: Long
)

class AnalysisViewModel(
    application: Application,
    private val repository: AnalysisRepository
) : AndroidViewModel(application) {

    enum class PeriodFilter(val displayName: String) {
        /** Bulan yang sedang dibuka di kalender. */
        THIS_MONTH("Bulan Ini"),
        /** Seluruh riwayat. */
        ALL_TIME("Semua Bulan")
    }

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.THIS_MONTH)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    private val _calendarMonth = MutableStateFlow(YearMonth.now())
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    fun setCalendarMonth(ym: YearMonth) {
        _calendarMonth.value = ym
    }

    /**
     * Catatan income + expense untuk periode aktif, diambil dalam satu query.
     *
     * Satu aliran dipakai untuk kedua garis supaya keranjang tanggalnya identik.
     * Kalau tiap tipe diambil terpisah, ada hari yang ada pengeluaran tapi tidak ada
     * pemasukan, sehingga kedua garis akan memakai jumlah titik berbeda dan tidak
     * saling menindih dengan benar.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val chartRecords: StateFlow<List<com.example.data.FinancialRecord>> = combine(
        _selectedPeriod, _calendarMonth
    ) { period, calendarMonth -> Pair(period, calendarMonth) }
        .flatMapLatest { (period, calendarMonth) ->
            val (start, end) = getChartTimeRange(period, calendarMonth)
            if (start != null && end != null) {
                repository.getRecordsInRange(start, end)
            } else {
                repository.getAllRecords()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Tren pengeluaran (garis merah). */
    val dailyTrends: StateFlow<List<DailyAggregation>> = combine(
        chartRecords, _selectedPeriod
    ) { records, period -> aggregateDaily(records, period, "expense") }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Tren pemasukan (garis hijau). */
    val incomeTrends: StateFlow<List<DailyAggregation>> = combine(
        chartRecords, _selectedPeriod
    ) { records, period -> aggregateDaily(records, period, "income") }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun aggregateDaily(
        records: List<com.example.data.FinancialRecord>,
        period: PeriodFilter,
        type: String
    ): List<DailyAggregation> {
        val format = if (period == PeriodFilter.ALL_TIME) {
            SimpleDateFormat("MMM yyyy", Locale.forLanguageTag("id-ID"))
        } else {
            SimpleDateFormat("dd/MM", Locale.forLanguageTag("id-ID"))
        }

        val map = mutableMapOf<String, Pair<Long, Long>>()
        records.forEach { record ->
            if (record.type != type) return@forEach
            val dateStr = format.format(java.util.Date(record.date))
            val existing = map[dateStr]
            map[dateStr] = if (existing == null) {
                Pair(record.date, record.amount)
            } else {
                Pair(existing.first, existing.second + record.amount)
            }
        }

        return map.entries.map { (dateStr, data) ->
            DailyAggregation(dateString = dateStr, dateMs = data.first, totalAmount = data.second)
        }.sortedBy { it.dateMs }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryAggregations: StateFlow<List<CategoryAggregation>> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getStatsTimeRange(period, calendarMonth)
        if (start != null && end != null) {
            repository.getExpenseAggregation(start, end)
        } else {
            repository.getAllTimeExpenseAggregation()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpense: StateFlow<Long> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getStatsTimeRange(period, calendarMonth)
        if (start != null && end != null) {
            repository.getTotalExpense(start, end).map { it ?: 0L }
        } else {
            repository.getAllTimeTotalExpense().map { it ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: StateFlow<Long> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getStatsTimeRange(period, calendarMonth)
        if (start != null && end != null) {
            repository.getTotalIncome(start, end).map { it ?: 0L }
        } else {
            repository.getAllTimeTotalIncome().map { it ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val topCategory: StateFlow<CategoryAggregation?> = categoryAggregations.map { list ->
        list.maxByOrNull { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val totalTransactions: StateFlow<Int> = categoryAggregations.map { list ->
        list.sumOf { it.transactionCount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setPeriod(period: PeriodFilter) {
        _selectedPeriod.value = period
    }

    private fun getStatsTimeRange(period: PeriodFilter, calendarMonth: YearMonth): Pair<Long?, Long?> {
        val zoneId = ZoneId.systemDefault()

        return when (period) {
            PeriodFilter.THIS_MONTH -> {
                val startMs = calendarMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMsSelected = calendarMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                startMs to endMsSelected
            }
            PeriodFilter.ALL_TIME -> null to null
        }
    }

    private fun getChartTimeRange(period: PeriodFilter, calendarMonth: YearMonth): Pair<Long?, Long?> {
        return getStatsTimeRange(period, calendarMonth)
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
                    val db = AppDatabase.getDatabase(application)
                    val repo = AnalysisRepository(db.analysisDao())
                    return AnalysisViewModel(application, repo) as T
                } else {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
