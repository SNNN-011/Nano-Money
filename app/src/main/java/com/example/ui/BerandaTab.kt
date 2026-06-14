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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    // Search Box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Cari transaksi...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", tint = GhostWhite.copy(alpha = 0.4f)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = GhostWhite.copy(alpha = 0.5f))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("search_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput
                        )
                    )

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

            // Search Bar for comfort phone interaction
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Cari transaksi...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", tint = GhostWhite.copy(alpha = 0.4f)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = GhostWhite.copy(alpha = 0.5f))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GhostWhite,
                    unfocusedTextColor = GhostWhite,
                    focusedBorderColor = SteelBlue,
                    unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                    focusedContainerColor = TranslucentInput,
                    unfocusedContainerColor = TranslucentInput
                )
            )

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
