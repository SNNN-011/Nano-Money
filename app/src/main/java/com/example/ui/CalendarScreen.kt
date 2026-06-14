package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

// Custom design system variables as specified
val CalBgUtama = Color(0xFF0D0C15) // MidnightAbyss
val CalBgCardSheet = Color(0xFF0D0C15).copy(alpha = 0.97f) // TranslucentForm
val CalTeksUtama = Color(0xFFFFFFFF)
val CalTeksSekunder = Color(0xFF9E9E9E)
val CalPemasukan = Color(0xFF4CAF50)
val CalPengeluaran = Color(0xFFF44336)

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

    // Monthly summary aggregation
    val currentMonthRecords = remember(transactionsByDate) {
        transactionsByDate.values.flatten()
    }
    val totalIncomeForMonth = remember(currentMonthRecords) {
        currentMonthRecords.filter { it.type == "income" }.sumOf { it.amount }
    }
    val totalExpenseForMonth = remember(currentMonthRecords) {
        currentMonthRecords.filter { it.type == "expense" }.sumOf { it.amount }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calendar Card Wrapper
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GhostWhite.copy(alpha = 0.2f),
                            GhostWhite.copy(alpha = 0.02f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header navigasi bulan
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
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya", tint = CalTeksUtama)
                        }

                        Text(
                            text = "${getIndonesianMonthName(currentYm.monthValue)} ${currentYm.year}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = CalTeksUtama,
                            modifier = Modifier.testTag("current_month_title")
                        )

                        IconButton(
                            onClick = {
                                calendarViewModel.setCurrentMonthYear(currentYm.plusMonths(1))
                            },
                            modifier = Modifier.testTag("next_month_btn")
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya", tint = CalTeksUtama)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Day column headers (Sen - Min)
                    val daysOfWeek = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        daysOfWeek.forEach { dayName ->
                            Text(
                                text = dayName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = CalTeksSekunder
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid Days cells
                    val rowsCount = daysInGrid.size / 7
                    for (r in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

                                        val cellBgModifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .then(
                                                when {
                                                    isSelected -> Modifier.background(CalTeksUtama) // Background putih, teks hitam
                                                    else -> Modifier.background(Color.Transparent)
                                                }
                                            )
                                            .clickable {
                                                calendarViewModel.setSelectedDate(dayDate)
                                                showBottomSheet = true
                                            }
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.2.dp, CalTeksUtama, CircleShape) // Outline putih
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .testTag("calendar_day_${dayDate.dayOfMonth}")

                                        Box(
                                            modifier = cellBgModifier,
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = dayDate.dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 15.sp
                                                    ),
                                                    color = if (isSelected) CalBgUtama else CalTeksUtama
                                                )

                                                // Draw indicators/dots
                                                if (dateRecords.isNotEmpty()) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        if (hasIncome) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .background(CalPemasukan, CircleShape)
                                                            )
                                                        }
                                                        if (hasExpense) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .background(CalPengeluaran, CircleShape)
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

            // Divider separating calendar and analysis
            HorizontalDivider(
                color = GhostWhite.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Include analysis content directly within Kalender screen
            AnalysisTabContent(viewModel = analysisViewModel)
        }
    }

    // Modal BottomSheet for Detail Transactions
    if (showBottomSheet) {

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = CalBgCardSheet,
            contentColor = CalTeksUtama,
            dragHandle = { BottomSheetDefaults.DragHandle(color = CalTeksSekunder.copy(alpha = 0.4f)) },
            modifier = Modifier.testTag("calendar_detail_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Date Title Indonesian
                Text(
                    text = getFormattedIndonesianHeaderDate(selectedDate),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CalTeksUtama,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (selectedDateTransactions.isEmpty()) {
                    // Empty state layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Empty",
                            tint = CalTeksSekunder,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Tidak ada transaksi di tanggal ini",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CalTeksSekunder,
                            textAlign = TextAlign.Center
                        )
                        PremiumButton(
                            text = "Tambah Transaksi",
                            onClick = {
                                onNavigateToNewTransaction(selectedDate)
                                showBottomSheet = false
                            },
                            isActive = true,
                            icon = Icons.Default.Add,
                            testTag = "bottom_sheet_add_trans_empty_btn",
                            fillMaxWidth = false
                        )
                    }
                } else {
                    // List of transactions
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = 16.dp)
                    ) {
                        items(selectedDateTransactions, key = { it.id }) { record ->
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
                            HorizontalDivider(color = CalTeksSekunder.copy(alpha = 0.1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Floating action button inside BottomSheet bottom area
                    PremiumButton(
                        text = "Tambah Transaksi",
                        onClick = {
                            onNavigateToNewTransaction(selectedDate)
                            showBottomSheet = false
                        },
                        isActive = true,
                        icon = Icons.Default.Add,
                        testTag = "bottom_sheet_add_trans_btn",
                        modifier = Modifier.fillMaxWidth()
                    )
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
