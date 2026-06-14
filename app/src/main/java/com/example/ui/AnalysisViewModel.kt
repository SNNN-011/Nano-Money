package com.example.ui

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

import java.text.SimpleDateFormat

data class DailyAggregation(
    val dateString: String,
    val dateMs: Long,
    val totalAmount: Double
)

class AnalysisViewModel(
    application: Application,
    private val repository: AnalysisRepository
) : AndroidViewModel(application) {

    enum class PeriodFilter(val displayName: String) {
        THIS_WEEK("Minggu"),
        THIS_MONTH("Bulan"),
        LAST_3_MONTHS("3 bulan"),
        ALL_TIME("Semua")
    }

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.THIS_MONTH)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    private val _calendarMonth = MutableStateFlow(YearMonth.now())
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    fun setCalendarMonth(ym: YearMonth) {
        _calendarMonth.value = ym
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyTrends: StateFlow<List<DailyAggregation>> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getChartTimeRange(period, calendarMonth)
        val recordsFlow = if (start != null && end != null) {
            repository.getExpenseRecords(start, end)
        } else {
            repository.getAllTimeExpenseRecords()
        }
        recordsFlow.map { records ->
            if (records.isEmpty()) return@map emptyList()
            
            val format = if (period == PeriodFilter.LAST_3_MONTHS || period == PeriodFilter.ALL_TIME) {
                SimpleDateFormat("MMM yyyy", Locale.forLanguageTag("id-ID"))
            } else {
                SimpleDateFormat("dd/MM", Locale.forLanguageTag("id-ID"))
            }
            
            val map = mutableMapOf<String, Pair<Long, Double>>()
            records.forEach { record ->
                val dateStr = format.format(java.util.Date(record.date))
                val existing = map[dateStr]
                if (existing == null) {
                    map[dateStr] = Pair(record.date, record.amount)
                } else {
                    map[dateStr] = Pair(existing.first, existing.second + record.amount)
                }
            }
            
            map.entries.map { (dateStr, data) ->
                DailyAggregation(dateString = dateStr, dateMs = data.first, totalAmount = data.second)
            }.sortedBy { it.dateMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
    val totalExpense: StateFlow<Double> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getStatsTimeRange(period, calendarMonth)
        if (start != null && end != null) {
            repository.getTotalExpense(start, end).map { it ?: 0.0 }
        } else {
            repository.getAllTimeTotalExpense().map { it ?: 0.0 }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: StateFlow<Double> = combine(_selectedPeriod, _calendarMonth) { period, calendarMonth ->
        Pair(period, calendarMonth)
    }.flatMapLatest { (period, calendarMonth) ->
        val (start, end) = getStatsTimeRange(period, calendarMonth)
        if (start != null && end != null) {
            repository.getTotalIncome(start, end).map { it ?: 0.0 }
        } else {
            repository.getAllTimeTotalIncome().map { it ?: 0.0 }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val averageDailyExpense: StateFlow<Double> = combine(
        totalExpense,
        combine(_selectedPeriod, _calendarMonth) { p, m -> Pair(p, m) },
        repository.getOldestExpenseDate()
    ) { total, (period, calendarMonth), oldestDateMs ->
        val days = getDaysInPeriod(period, calendarMonth, oldestDateMs)
        if (days > 0) total / days else 0.0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    
    val topCategory: StateFlow<CategoryAggregation?> = categoryAggregations.map { list ->
        list.maxByOrNull { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val totalTransactions: StateFlow<Int> = categoryAggregations.map { list ->
        list.sumOf { it.transactionCount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setPeriod(period: PeriodFilter) {
        _selectedPeriod.value = period
    }

    private fun getDaysInPeriod(period: PeriodFilter, calendarMonth: YearMonth, oldestDateMs: Long?): Int {
        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        return when (period) {
            PeriodFilter.THIS_WEEK -> {
                7
            }
            PeriodFilter.THIS_MONTH -> calendarMonth.lengthOfMonth()
            PeriodFilter.LAST_3_MONTHS -> {
                val start = now.minusMonths(3)
                java.time.temporal.ChronoUnit.DAYS.between(start, now).toInt() + 1
            }
            PeriodFilter.ALL_TIME -> {
                if (oldestDateMs == null) return 1
                val oldest = java.time.Instant.ofEpochMilli(oldestDateMs)
                    .atZone(zoneId).toLocalDate()
                java.time.temporal.ChronoUnit.DAYS.between(oldest, now).toInt().coerceAtLeast(1)
            }
        }
    }

    private fun getStatsTimeRange(period: PeriodFilter, calendarMonth: YearMonth): Pair<Long?, Long?> {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDate.now()
        val endMs = now.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return when (period) {
            PeriodFilter.THIS_WEEK -> {
                val currentDayOfWeek = now.dayOfWeek.value
                val firstDayOfWeek = now.minusDays((currentDayOfWeek - 1).toLong())
                val startMs = firstDayOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
                startMs to endMs
            }
            PeriodFilter.THIS_MONTH -> {
                val startMs = calendarMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMsSelected = calendarMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                startMs to endMsSelected
            }
            PeriodFilter.LAST_3_MONTHS -> {
                val startMs = now.minusMonths(3).withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                startMs to endMs
            }
            PeriodFilter.ALL_TIME -> null to null
        }
    }

    private fun getChartTimeRange(period: PeriodFilter, calendarMonth: YearMonth): Pair<Long?, Long?> {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDate.now()

        return when (period) {
            PeriodFilter.THIS_WEEK -> {
                val currentMonth = YearMonth.now()
                val startMs = currentMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMs = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                startMs to endMs
            }
            PeriodFilter.THIS_MONTH -> {
                val startMs = calendarMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMsSelected = calendarMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                startMs to endMsSelected
            }
            PeriodFilter.LAST_3_MONTHS -> {
                val startMs = now.minusMonths(3).withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMs = YearMonth.now().atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                startMs to endMs
            }
            PeriodFilter.ALL_TIME -> null to null
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
                    val db = AppDatabase.getDatabase(application)
                    val repo = AnalysisRepository(db.analysisDao())
                    return AnalysisViewModel(application, repo) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
