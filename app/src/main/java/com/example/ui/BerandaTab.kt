package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.data.RecurringTransaction
import com.example.ui.theme.*

@Composable
fun BerandaTabContent(
    isWideScreen: Boolean,
    totalIncome: Double,
    totalExpense: Double,
    currentBalance: Double,
    monthlySpendingTotal: Double,
    monthlyBudgetLimit: Double,
    categoryBudgets: Map<String, Double> = emptyMap(),
    categorySpending: Map<String, Double> = emptyMap(),
    selectedBudgetOffset: Int = 0,
    onBudgetOffsetChange: (Int) -> Unit = {},
    onSetBudgetClick: () -> Unit,
    filteredRecords: List<FinancialRecord>,
    filterType: String,
    sortByNewest: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (String) -> Unit,
    onSortToggled: () -> Unit,
    onEditRecord: (FinancialRecord) -> Unit,
    onDeleteRecord: (FinancialRecord) -> Unit,
    onSeedSampleData: () -> Unit = {},
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    incomeCategories: List<String> = emptyList(),
    expenseCategories: List<String> = emptyList(),
    onAddRecurring: (description: String, amount: Double, type: String, category: String, dayOfMonth: Int, notes: String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteRecurring: (RecurringTransaction) -> Unit = {},
    onToggleRecurringActive: (RecurringTransaction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showRecurringDialog by remember { mutableStateOf(false) }
    if (isWideScreen) {
        // TABLET / WIDE SCREENS LAYOUT
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left column: stats & brief overview
            Column(
                modifier = Modifier.weight(4f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardStatsSection(
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    currentBalance = currentBalance,
                    monthlySpendingTotal = monthlySpendingTotal,
                    monthlyBudgetLimit = monthlyBudgetLimit,
                    categoryBudgets = categoryBudgets,
                    categorySpending = categorySpending,
                    selectedBudgetOffset = selectedBudgetOffset,
                    onBudgetOffsetChange = onBudgetOffsetChange,
                    onSetBudgetClick = onSetBudgetClick,
                    onRecurringClick = { showRecurringDialog = true }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Catatan Keuangan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kelola pemasukan dan pengeluaran harian dalam satu aplikasi yang aman & modern. Data Anda dapat diekspor kapan saja lewat halaman cadangan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Right column: complete filter + searchable transaction list tables
            Card(
                modifier = Modifier.weight(6f).fillMaxHeight(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp,
                                    color = GhostWhite.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(TranslucentInput, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Cari", tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Cari...", color = GhostWhite.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = GhostWhite),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("search_input"),
                                    cursorBrush = SolidColor(SteelBlue)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // High aesthetic Custom sorting toggle button
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp,
                                    color = GhostWhite.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSortToggled() }
                                .padding(horizontal = 14.dp)
                                .testTag("history_sort_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (sortByNewest) "Terbaru" else "Terlama",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GhostWhite
                                )
                                Icon(
                                    imageVector = if (sortByNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Urutkan",
                                    tint = GhostWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilterAndSortHeader(
                        currentFilter = filterType,
                        isNewest = sortByNewest,
                        onFilterSelected = onFilterSelected,
                        onSortToggled = onSortToggled
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredRecords.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyStatePlaceholder(onSeedData = onSeedSampleData)
                        }
                    } else {
                        TransactionList(
                            records = filteredRecords,
                            onEdit = onEditRecord,
                            onDelete = onDeleteRecord,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    } else {
        // MOBILE COMFORT LAYOUT
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardStatsSection(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                currentBalance = currentBalance,
                monthlySpendingTotal = monthlySpendingTotal,
                monthlyBudgetLimit = monthlyBudgetLimit,
                categoryBudgets = categoryBudgets,
                categorySpending = categorySpending,
                selectedBudgetOffset = selectedBudgetOffset,
                onBudgetOffsetChange = onBudgetOffsetChange,
                onSetBudgetClick = onSetBudgetClick,
                onRecurringClick = { showRecurringDialog = true }
            )

            FilterAndSortHeader(
                currentFilter = filterType,
                isNewest = sortByNewest,
                onFilterSelected = onFilterSelected,
                onSortToggled = onSortToggled
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minimalist Search Bar
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = GhostWhite.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(TranslucentInput, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Cari", tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text("Cari...", color = GhostWhite.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = GhostWhite),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("search_input"),
                            cursorBrush = SolidColor(SteelBlue)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // High aesthetic Custom sorting toggle button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = GhostWhite.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSortToggled() }
                        .padding(horizontal = 14.dp)
                        .testTag("history_sort_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (sortByNewest) "Terbaru" else "Terlama",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite
                        )
                        Icon(
                            imageVector = if (sortByNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Urutkan",
                            tint = GhostWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePlaceholder(onSeedData = onSeedSampleData)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredRecords.size, key = { filteredRecords[it].id }) { idx ->
                        val rec = filteredRecords[idx]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                            border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.05f))
                        ) {
                            TransactionListItem(
                                record = rec,
                                onEdit = { onEditRecord(rec) },
                                onDelete = { onDeleteRecord(rec) }
                            )
                        }
                    }
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
