package com.example.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.FinancialRecord
import com.example.data.RecurringTransaction
import com.example.ui.common.RecurringTransactionManagementDialog
import com.example.ui.components.BezelCard
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.theme.EditorialTypography
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Spacing

/**
 * Tab Dashboard.
 *
 * Susunannya mengikuti referensi portfolio: blok hero (label mono + angka
 * serif besar + daftar fakta bergaris rambut), kartu anggaran double-bezel,
 * lalu daftar riwayat yang dipisah garis rambut alih-alih kartu per baris.
 * Latar halaman krem dipasang di FinancialTrackerScreen (lapisan ambient per
 * tab), jadi di sini tidak ada latar lagi.
 */
@Composable
fun BerandaTabContent(
    isWideScreen: Boolean,
    totalIncome: Long,
    totalExpense: Long,
    currentBalance: Long,
    monthlySpendingTotal: Long,
    monthlyBudgetLimit: Long,
    categoryBudgets: Map<String, Long> = emptyMap(),
    // Tanpa default, lihat catatan di DashboardStatsSection.
    categoryNameById: Map<String, String>,
    balanceVisible: Boolean = true,
    onToggleBalanceVisible: () -> Unit = {},
    categorySpending: Map<String, Long> = emptyMap(),
    selectedBudgetOffset: Int = 0,
    onBudgetOffsetChange: (Int) -> Unit = {},
    onSetBudgetClick: () -> Unit,
    onDeleteBudgetClick: () -> Unit,
    filteredRecords: List<FinancialRecord>,
    filterType: String,
    sortByNewest: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (String) -> Unit,
    onSortToggled: () -> Unit,
    isFilterActive: Boolean = false,
    onClearFilters: () -> Unit = {},
    onEditRecord: (FinancialRecord) -> Unit,
    onDeleteRecord: (FinancialRecord) -> Unit,
    onDeleteAllClick: () -> Unit = {},
    onSeedSampleData: () -> Unit = {},
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    incomeCategories: List<String> = emptyList(),
    expenseCategories: List<String> = emptyList(),
    onAddRecurring: (description: String, amount: Long, type: String, category: String, dayOfMonth: Int, notes: String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteRecurring: (RecurringTransaction) -> Unit = {},
    onToggleRecurringActive: (RecurringTransaction) -> Unit = {},
    welcomeMessage: String = "",
    welcomeMessageColor: String = "",
    showNewFeature: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showRecurringDialog by remember { mutableStateOf(false) }

    // Skala tipe editorial hanya dipasang untuk tab ini sampai tab lain ikut
    // di-redesign. Setelah seluruh aplikasi memakai palet editorial, pindahkan
    // EditorialTypography ke MyApplicationTheme lalu hapus pembungkus ini.
    MaterialTheme(typography = EditorialTypography) {

        if (isWideScreen) {
            // TABLET / WIDE SCREENS LAYOUT
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Left column: stats & brief overview
                Column(
                    modifier = Modifier
                        .weight(4f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    DashboardStatsSection(
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        currentBalance = currentBalance,
                        monthlySpendingTotal = monthlySpendingTotal,
                        monthlyBudgetLimit = monthlyBudgetLimit,
                        categoryBudgets = categoryBudgets,
                        categoryNameById = categoryNameById,
                        balanceVisible = balanceVisible,
                        onToggleBalanceVisible = onToggleBalanceVisible,
                        categorySpending = categorySpending,
                        selectedBudgetOffset = selectedBudgetOffset,
                        onBudgetOffsetChange = onBudgetOffsetChange,
                        onSetBudgetClick = onSetBudgetClick,
                        onDeleteBudgetClick = onDeleteBudgetClick,
                        onRecurringClick = { showRecurringDialog = true },
                        welcomeMessage = welcomeMessage,
                        welcomeMessageColor = welcomeMessageColor,
                        showNewFeature = showNewFeature
                    )

                    BezelCard {
                        Eyebrow(text = "Catatan keuangan")
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Kelola pemasukan dan pengeluaran harian dalam satu aplikasi yang aman & modern. Data Anda dapat diekspor kapan saja lewat halaman cadangan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }
                }

                // Right column: complete filter + searchable transaction list tables
                BezelCard(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight(),
                    contentPadding = Spacing.lg
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DashboardHistoryFilter(
                            currentFilter = filterType,
                            onFilterSelected = onFilterSelected,
                            modifier = Modifier.padding(bottom = Spacing.md)
                        )
                        DashboardSearchActions(
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            sortByNewest = sortByNewest,
                            onSortToggled = onSortToggled,
                            onDeleteAllClick = onDeleteAllClick,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )

                        if (filteredRecords.isEmpty()) {
                            DashboardEmptyState(
                                isFiltered = isFilterActive,
                                onSeedData = onSeedSampleData,
                                onClearFilters = onClearFilters
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(
                                    items = filteredRecords,
                                    key = { _, record -> record.id },
                                    contentType = { _, _ -> "transaction" }
                                ) { index, record ->
                                    DashboardTransactionRow(
                                        record = record,
                                        categoryNameById = categoryNameById,
                                        onEdit = { onEditRecord(record) },
                                        onDelete = { onDeleteRecord(record) }
                                    )
                                    if (index < filteredRecords.lastIndex) HairlineDivider()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // MOBILE COMFORT LAYOUT
            // Satu permukaan scroll untuk seluruh halaman. Sebelumnya header memakai banyak
            // ruang dan daftar transaksi memakai weight(1f), jadi sisa ruang untuk daftar
            // bisa tinggal setinggi satu-dua baris. Semua transaksi jadi item dalam satu
            // LazyColumn supaya halaman bisa digulir utuh.
            val listState = rememberLazyListState()
            LaunchedEffect(filteredRecords) {
                if (filteredRecords.isNotEmpty()) listState.scrollToItem(0)
            }

            // Tanpa verticalArrangement: jarak antar blok diatur di dalam tiap item,
            // karena baris transaksi harus rapat (dipisah garis rambut, bukan jarak).
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg),
                contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xxl)
            ) {
                // Item header dipisah dari daftar transaksi. Kalau satu item memuat seluruh
                // blok statistik beserta anggaran per kategori, setiap perubahan data di
                // dashboard membuat subtree besar itu ikut recomposition.
                item(key = "dashboard_stats", contentType = "header") {
                    DashboardStatsSection(
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        currentBalance = currentBalance,
                        monthlySpendingTotal = monthlySpendingTotal,
                        monthlyBudgetLimit = monthlyBudgetLimit,
                        categoryBudgets = categoryBudgets,
                        categoryNameById = categoryNameById,
                        balanceVisible = balanceVisible,
                        onToggleBalanceVisible = onToggleBalanceVisible,
                        categorySpending = categorySpending,
                        selectedBudgetOffset = selectedBudgetOffset,
                        onBudgetOffsetChange = onBudgetOffsetChange,
                        onSetBudgetClick = onSetBudgetClick,
                        onDeleteBudgetClick = onDeleteBudgetClick,
                        onRecurringClick = { showRecurringDialog = true },
                        welcomeMessage = welcomeMessage,
                        welcomeMessageColor = welcomeMessageColor,
                        showNewFeature = showNewFeature,
                        modifier = Modifier.padding(bottom = Spacing.xl)
                    )
                }

                item(key = "filter_sort", contentType = "header") {
                    DashboardHistoryFilter(
                        currentFilter = filterType,
                        onFilterSelected = onFilterSelected,
                        modifier = Modifier.padding(bottom = Spacing.md)
                    )
                }

                item(key = "search_actions", contentType = "header") {
                    DashboardSearchActions(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        sortByNewest = sortByNewest,
                        onSortToggled = onSortToggled,
                        onDeleteAllClick = onDeleteAllClick,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }

                if (filteredRecords.isEmpty()) {
                    item(key = "empty_state", contentType = "header") {
                        DashboardEmptyState(
                            isFiltered = isFilterActive,
                            onSeedData = onSeedSampleData,
                            onClearFilters = onClearFilters
                        )
                    }
                } else {
                    // contentType wajib diisi: tanpa itu Compose menganggap semua slot
                    // item berbeda jenis dan tidak bisa memakai ulang node yang sama
                    // saat item berganti masuk viewport, sehingga setiap gulir menyusun
                    // ulang subtree dari nol.
                    itemsIndexed(
                        items = filteredRecords,
                        key = { _, record -> record.id },
                        contentType = { _, _ -> "transaction" }
                    ) { index, record ->
                        DashboardTransactionRow(
                            record = record,
                            categoryNameById = categoryNameById,
                            onEdit = { onEditRecord(record) },
                            onDelete = { onDeleteRecord(record) }
                        )
                        if (index < filteredRecords.lastIndex) HairlineDivider()
                    }
                }
            }
        }

        if (showRecurringDialog) {
            RecurringTransactionManagementDialog(
                recurringTransactions = recurringTransactions,
                incomeCategories = incomeCategories,
                expenseCategories = expenseCategories,
                onAddRecurring = onAddRecurring,
                onDeleteRecurring = onDeleteRecurring,
                onToggleRecurringActive = onToggleRecurringActive,
                onDismissRequest = { showRecurringDialog = false }
            )
        }
    }
}
