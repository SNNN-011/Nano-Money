package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(
    application: Application,
    private val repository: FinancialRecordRepository
) : AndroidViewModel(application) {

    val selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val currentMonthYear = MutableStateFlow<YearMonth>(YearMonth.now())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactionsByDate: StateFlow<Map<LocalDate, List<FinancialRecord>>> = currentMonthYear
        .flatMapLatest { ym ->
            repository.getTransactionsByMonth(ym.year, ym.monthValue)
                .map { list ->
                    val zoneId = java.time.ZoneId.systemDefault()
                    list.groupBy { record ->
                        try {
                            java.time.Instant.ofEpochMilli(record.date)
                                .atZone(zoneId)
                                .toLocalDate()
                        } catch (e: Exception) {
                            java.time.LocalDate.now()
                        }
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateTransactions: StateFlow<List<FinancialRecord>> = selectedDate
        .flatMapLatest { date ->
            repository.getTransactionsByDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setCurrentMonthYear(ym: YearMonth) {
        currentMonthYear.value = ym
    }

    fun deleteRecord(record: FinancialRecord) {
        viewModelScope.launch {
            repository.delete(record)
            com.example.util.FirebaseSyncHelper.deleteRecordFromFirestoreDirectly(record.id)
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = FinancialRecordRepository(database.financialRecordDao())
                return CalendarViewModel(application, repository) as T
            }
        }
    }
}
