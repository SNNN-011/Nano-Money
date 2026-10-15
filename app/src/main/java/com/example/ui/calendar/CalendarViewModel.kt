package com.example.ui.calendar

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
                    // Tanggal rusak TIDAK dijatuhkan ke hari ini: dulu fallback-nya
                    // LocalDate.now(), sehingga uang dari record bertanggal rusak
                    // "menyusup" ke total hari ini. Sekarang record seperti itu
                    // dilewati (dan dicatat di log) alih-alih mengotori agregat.
                    list.mapNotNull { record ->
                        val date = if (record.date > 0L) {
                            try {
                                java.time.Instant.ofEpochMilli(record.date)
                                    .atZone(zoneId)
                                    .toLocalDate()
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        if (date == null) {
                            com.example.util.SecureLog.w(
                                "CalendarVM",
                                "Tanggal tidak valid untuk record ${record.id}: ${record.date}; dilewati dari kalender"
                            )
                        }
                        date?.let { it to record }
                    }.groupBy({ it.first }, { it.second })
                }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
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
        val clamped = ym.coerceIn(MIN_MONTH, maxMonth())
        currentMonthYear.value = clamped

        // Tanggal terpilih ikut bulan yang sedang dibuka; harinya di-clamp ke
        // panjang bulan tujuan (31 Jan pindah ke Februari -> 28/29 Feb) supaya
        // bottom sheet tidak menampilkan tanggal "hantu" dari bulan lain.
        val selected = selectedDate.value
        if (YearMonth.from(selected) != clamped) {
            selectedDate.value = clampedDateForMonth(selected, clamped)
        }
    }

    fun deleteRecord(record: FinancialRecord) {
        viewModelScope.launch {
            repository.delete(record)
            if (com.example.util.FirebaseSyncHelper.isUserSignedIn()) {
                runCatching { com.example.util.FirebaseSyncHelper.pushPending(getApplication()) }
            }
        }
    }

    companion object {
        /**
         * Batas navigasi bulan: tidak ada data sebelum 1970 dan tidak masuk jauh
         * ke masa depan. Sekaligus mencegah YearMonth melampaui batas tahun yang
         * membuat plusMonths/minusMonths melempar DateTimeException.
         */
        private val MIN_MONTH: YearMonth = YearMonth.of(1970, 1)

        private fun maxMonth(): YearMonth = YearMonth.now().plusYears(1)

        /** Hari yang sama di bulan tujuan, di-clamp ke panjang bulan (31 Jan -> 28/29 Feb). */
        internal fun clampedDateForMonth(date: LocalDate, target: YearMonth): LocalDate =
            target.atDay(date.dayOfMonth.coerceAtMost(target.lengthOfMonth()))

        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(application)
                val repository = FinancialRecordRepository.create(database)
                return CalendarViewModel(application, repository) as T
            }
        }
    }
}

