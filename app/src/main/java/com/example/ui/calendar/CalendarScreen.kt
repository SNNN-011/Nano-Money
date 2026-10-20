package com.example.ui.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.components.BezelCard
import com.example.ui.components.EditorialButton
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.common.FinancialTrackerViewModel
import com.example.ui.analysis.AnalysisViewModel
import com.example.ui.analysis.AnalysisTabContent
import com.example.ui.analysis.PeriodFilterRow
import com.example.ui.transaction.TransactionListItem
import java.time.LocalDate

/**
 * Warna bersama kalender + grafik latarnya. Nama lama dipertahankan karena
 * CalendarTrendBackdrop dan tab Analisa memakai konstanta yang sama; isinya
 * sekarang token editorial, bukan palet gelap.
 */
val CalBgUtama = Cream
val CalBgCardSheet = CreamRaised
val CalTeksUtama = Ink
val CalTeksSekunder = InkMuted
val CalPemasukan = ForestGreen
val CalPengeluaran = BurntOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTabContent(
    calendarViewModel: CalendarViewModel,
    mainViewModel: FinancialTrackerViewModel,
    analysisViewModel: AnalysisViewModel,
    onNavigateToNewTransaction: (LocalDate) -> Unit,
    onNavigateToEditTransaction: (FinancialRecord) -> Unit,
    onDeleteRecord: (FinancialRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDate by calendarViewModel.selectedDate.collectAsState()
    val currentYm by calendarViewModel.currentMonthYear.collectAsState()
    val transactionsByDate by calendarViewModel.transactionsByDate.collectAsState()
    val selectedDateTransactions by calendarViewModel.selectedDateTransactions.collectAsState()

    // Chart latar mengikuti bulan yang sedang dibuka di kalender. Sebelumnya
    // AnalysisViewModel punya bulan sendiri yang tidak pernah disinkronkan, jadi
    // chart bisa menampilkan bulan yang berbeda dari kalender.
    val backgroundExpenseTrends by analysisViewModel.dailyTrends.collectAsState()
    val backgroundIncomeTrends by analysisViewModel.incomeTrends.collectAsState()
    val chartPeriod by analysisViewModel.selectedPeriod.collectAsState()

    LaunchedEffect(currentYm) {
        analysisViewModel.setCalendarMonth(currentYm)
    }

    var showBottomSheet by remember { mutableStateOf(false) }

    // Prepare calendar grid
    val firstOfMonth = currentYm.atDay(1)
    val dayOfWeekValue = firstOfMonth.dayOfWeek.value // 1 (Monday) to 7 (Sunday)
    val totalDaysInMonth = currentYm.lengthOfMonth()

    val daysInGrid = remember(currentYm) {
        val list = ArrayList<LocalDate?>()
        // Leading empty cells
        for (i in 0 until (dayOfWeekValue - 1)) {
            list.add(null)
        }
        // Actual month days
        for (day in 1..totalDaysInMonth) {
            list.add(currentYm.atDay(day))
        }
        // Trailing empty cells
        while (list.size % 7 != 0) {
            list.add(null)
        }
        list
    }

    MaterialTheme(typography = EditorialTypography) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Spacer(modifier = Modifier.height(Spacing.lg))

                Eyebrow(text = "Kalender")

                // Navigasi bulan: panah tinta di ujung, nama bulan serif di tengah.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            calendarViewModel.setCurrentMonthYear(currentYm.minusMonths(1))
                        },
                        modifier = Modifier.testTag("prev_month_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Bulan sebelumnya",
                            tint = Ink
                        )
                    }

                    Text(
                        text = "${getIndonesianMonthName(currentYm.monthValue)} ${currentYm.year}",
                        style = MaterialTheme.typography.displaySmall,
                        color = Ink,
                        modifier = Modifier.testTag("current_month_title")
                    )

                    IconButton(
                        onClick = {
                            calendarViewModel.setCurrentMonthYear(currentYm.plusMonths(1))
                        },
                        modifier = Modifier.testTag("next_month_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Bulan berikutnya",
                            tint = Ink
                        )
                    }
                }

                // Satu permukaan, sama seperti kartu saldo di Dashboard. Grafik
                // tren digambar lebih dulu di dalam Box ini supaya berada di
                // belakang sel tanggal, bukan menutupinya.
                BezelCard(contentPadding = Spacing.md) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CalendarTrendBackdrop(
                            expenseTrends = backgroundExpenseTrends,
                            incomeTrends = backgroundIncomeTrends,
                            modifier = Modifier.matchParentSize()
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Kepala kolom hari: mono kecil, bukan tebal.
                            val daysOfWeek = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                            Row(modifier = Modifier.fillMaxWidth()) {
                                daysOfWeek.forEach { dayName ->
                                    Text(
                                        text = dayName,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            val rowsCount = daysInGrid.size / 7
                            for (r in 0 until rowsCount) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (c in 0..6) {
                                        val dayDate = daysInGrid[r * 7 + c]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (dayDate != null) {
                                                val isToday = dayDate == LocalDate.now()
                                                val isSelected = dayDate == selectedDate
                                                val dateRecords = transactionsByDate[dayDate] ?: emptyList()
                                                val hasIncome = dateRecords.any { it.type == "income" }
                                                val hasExpense = dateRecords.any { it.type == "expense" }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                        .then(
                                                            if (isSelected) Modifier.background(Ink)
                                                            else Modifier
                                                        )
                                                        .clickable {
                                                            calendarViewModel.setSelectedDate(dayDate)
                                                            showBottomSheet = true
                                                        }
                                                        .then(
                                                            if (isToday && !isSelected) {
                                                                Modifier.border(1.dp, Ink, CircleShape)
                                                            } else {
                                                                Modifier
                                                            }
                                                        )
                                                        .testTag("calendar_day_${dayDate.dayOfMonth}"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = dayDate.dayOfMonth.toString(),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected || isToday) {
                                                                FontWeight.SemiBold
                                                            } else {
                                                                FontWeight.Normal
                                                            },
                                                            color = if (isSelected) Cream else Ink
                                                        )

                                                        // Titik penanda: hijau pemasukan, jingga
                                                        // pengeluaran. Hanya penanda, angkanya ada
                                                        // di lembar bawah.
                                                        if (dateRecords.isNotEmpty()) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                if (hasIncome) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .background(
                                                                                if (isSelected) Cream else CalPemasukan,
                                                                                CircleShape
                                                                            )
                                                                    )
                                                                }
                                                                if (hasExpense) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .background(
                                                                                if (isSelected) Cream else CalPengeluaran,
                                                                                CircleShape
                                                                            )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pemilih periode memakai pil yang sama dengan filter riwayat di
                // Dashboard, supaya dua kontrol sejenis tidak tampil beda.
                PeriodFilterRow(
                    selectedPeriod = chartPeriod,
                    onPeriodSelect = { analysisViewModel.setPeriod(it) }
                )

                // Ringkasan angka dan rincian kategori. Grafiknya sudah pindah ke
                // belakang kalender, jadi tidak perlu diulang di sini.
                AnalysisTabContent(viewModel = analysisViewModel)

                Spacer(modifier = Modifier.height(Spacing.xxl))
            }

            // Latar redup saat lembar tanggal terbuka.
            AnimatedVisibility(
                visible = showBottomSheet,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink.copy(alpha = 0.35f))
                        .clickable { showBottomSheet = false }
                )
            }

            // Lembar transaksi per tanggal: krem, sudut atas membulat, satu
            // tombol aksi di bawah.
            AnimatedVisibility(
                visible = showBottomSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(
                        topStart = CornerRadius.bezel,
                        topEnd = CornerRadius.bezel
                    ),
                    colors = CardDefaults.cardColors(containerColor = CreamRaised),
                    border = BorderStroke(1.dp, Hairline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                    ) {
                        // Pegangan geser: garis rambut di tengah.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .background(HairlineStrong, RoundedCornerShape(2.dp))
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Eyebrow(text = "Transaksi di tanggal ini")
                        Text(
                            text = getFormattedIndonesianHeaderDate(selectedDate),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = SerifFamily,
                            color = Ink,
                            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md)
                        )

                        if (selectedDateTransactions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = InkMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Belum ada transaksi di tanggal ini",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted,
                                    textAlign = TextAlign.Center
                                )
                                EditorialButton(
                                    text = "Tambah transaksi",
                                    onClick = {
                                        onNavigateToNewTransaction(selectedDate)
                                        showBottomSheet = false
                                    },
                                    testTag = "bottom_sheet_add_trans_empty_btn"
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    itemsIndexed(
                                        selectedDateTransactions,
                                        key = { _, record -> record.id }
                                    ) { index, record ->
                                        TransactionListItem(
                                            record = record,
                                            onEdit = {
                                                onNavigateToEditTransaction(record)
                                                showBottomSheet = false
                                            },
                                            onDelete = {
                                                onDeleteRecord(record)
                                            }
                                        )
                                        if (index < selectedDateTransactions.lastIndex) {
                                            HairlineDivider()
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            EditorialButton(
                                text = "Tambah transaksi",
                                onClick = {
                                    onNavigateToNewTransaction(selectedDate)
                                    showBottomSheet = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                expanded = true,
                                testTag = "bottom_sheet_add_trans_btn"
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers
fun getIndonesianMonthName(month: Int): String {
    val months = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    return if (month in 1..12) months[month] else ""
}

fun getFormattedIndonesianHeaderDate(date: LocalDate): String {
    val dayOfWeekIndonesian = when (date.dayOfWeek.value) {
        1 -> "Senin"
        2 -> "Selasa"
        3 -> "Rabu"
        4 -> "Kamis"
        5 -> "Jumat"
        6 -> "Sabtu"
        7 -> "Minggu"
        else -> ""
    }
    return "$dayOfWeekIndonesian, ${date.dayOfMonth} ${getIndonesianMonthName(date.monthValue)} ${date.year}"
}
