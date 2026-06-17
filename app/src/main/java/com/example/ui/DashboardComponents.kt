package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun DashboardStatsSection(
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
    onRecurringClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var balanceVisible by remember { mutableStateOf(true) }
    var showRutinInfo by remember { mutableStateOf(false) }

    val displayBalance = if (balanceVisible) {
        FormatUtils.formatRupiah(currentBalance)
    } else {
        "Rp ••••••"
    }

    val displayIncome = if (balanceVisible) {
        "+" + FormatUtils.formatRupiah(totalIncome)
    } else {
        "+Rp ••••••"
    }

    val displayExpense = if (balanceVisible) {
        "-" + FormatUtils.formatRupiah(totalExpense)
    } else {
        "-Rp ••••••"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SALDO TOTAL",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = GhostWhite.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("total_balance_label")
                    )
                    IconButton(
                        onClick = { balanceVisible = !balanceVisible },
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("toggle_balance_visibility")
                    ) {
                        Icon(
                            imageVector = if (balanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (balanceVisible) "Sembunyikan Saldo" else "Tampilkan Saldo",
                            tint = GhostWhite.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showRutinInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Transaksi Rutin",
                            tint = GhostWhite.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Minimalist premium-styled Rutin button next to SALDO TOTAL
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SteelBlue.copy(alpha = 0.12f))
                            .border(1.dp, SteelBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .clickable { onRecurringClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("recurring_transactions_inline_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = SteelBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Rutin",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = SteelBlue
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayBalance,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-1).sp,
                    fontSize = 26.sp
                ),
                color = GhostWhite,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Income
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("total_income_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PENDAPATAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = GhostWhite.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayIncome,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = NeonGreen
                    )
                }

                // Decorative Center Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, GhostWhite.copy(alpha = 0.2f), Color.Transparent)
                            )
                        )
                )

                // Expense
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("total_expense_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PENGELUARAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = GhostWhite.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = NeonRed,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayExpense,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = NeonRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GhostWhite.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Unified Month Switcher Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onBudgetOffsetChange(selectedBudgetOffset - 1) },
                    modifier = Modifier.size(24.dp).testTag("prev_budget_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Bulan Sebelumnya",
                        tint = GhostWhite.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                val labelText = {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.MONTH, selectedBudgetOffset)
                    val monthNames = arrayOf(
                        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
                    )
                    val monthStr = monthNames[cal.get(java.util.Calendar.MONTH)]
                    val year = cal.get(java.util.Calendar.YEAR)
                    val prefix = "ANGGARAN"
                    if (selectedBudgetOffset == 0) {
                        "$prefix: $monthStr $year (BULAN INI)"
                    } else {
                        "$prefix: $monthStr $year"
                    }
                }()

                Text(
                    text = labelText.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = SteelBlue,
                    modifier = Modifier.testTag("budget_current_month_label")
                )

                IconButton(
                    onClick = { onBudgetOffsetChange(selectedBudgetOffset + 1) },
                    modifier = Modifier.size(24.dp).testTag("next_budget_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Bulan Selanjutnya",
                        tint = GhostWhite.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (monthlyBudgetLimit <= 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Atur batas belanja bulanan Anda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }
                    PremiumButton(
                        text = "Atur",
                        onClick = onSetBudgetClick,
                        isActive = true,
                        testTag = "set_budget_cta_button",
                        fillMaxWidth = false,
                        horizontalPadding = 14.dp,
                        verticalPadding = 6.dp
                    )
                }
            } else {
                val progress = if (balanceVisible) (monthlySpendingTotal / monthlyBudgetLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                val isOverBudget = monthlySpendingTotal > monthlyBudgetLimit
                val isWarningPercent = (monthlySpendingTotal / monthlyBudgetLimit) >= 0.8
                
                val progressColor = when {
                    !balanceVisible -> GhostWhite.copy(alpha = 0.2f)
                    isOverBudget -> NeonRed
                    isWarningPercent -> Color(0xFFFFA000)
                    else -> SteelBlue
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(progressColor, shape = CircleShape)
                        )
                        Text(
                            text = if (isOverBudget) "OVER BUDGET" else "BELANJA AMAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = progressColor
                        )
                    }
                    IconButton(
                        onClick = onSetBudgetClick,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("edit_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Anggaran",
                            tint = GhostWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GhostWhite.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(progressColor)
                            .testTag("budget_progress_bar_fill")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (!balanceVisible) {
                                "Sisa: Rp ••••••"
                            } else if (isOverBudget) {
                                "Sisa: " + FormatUtils.formatRupiah(0.0) + " (Over " + FormatUtils.formatRupiah(monthlySpendingTotal - monthlyBudgetLimit) + ")"
                            } else {
                                "Sisa: " + FormatUtils.formatRupiah(monthlyBudgetLimit - monthlySpendingTotal)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (!balanceVisible) SteelBlue else if (isOverBudget) NeonRed else SteelBlue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (balanceVisible) {
                                "${FormatUtils.formatRupiahCompact(monthlySpendingTotal)} terpakai dari ${FormatUtils.formatRupiahCompact(monthlyBudgetLimit)}"
                            } else {
                                "Rp •••••• terpakai dari Rp ••••••"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }

                    val percentInt = ((monthlySpendingTotal / monthlyBudgetLimit) * 100).toInt()
                    Box(
                        modifier = Modifier
                            .background(
                                color = progressColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, progressColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (balanceVisible) "$percentInt%" else "••%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                    }
                }

                val activeCategoryBudgets = categoryBudgets.filter { it.value > 0.0 }
                if (activeCategoryBudgets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(GhostWhite.copy(alpha = 0.05f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var isExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SteelBlue, shape = CircleShape)
                            )
                            Text(
                                text = "ANGGARAN PER KATEGORI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = SteelBlue
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (isExpanded) "Sembunyikan" else "Tampilkan",
                            tint = GhostWhite.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            activeCategoryBudgets.forEach { (cat, limit) ->
                                val spent = if (balanceVisible) (categorySpending[cat] ?: 0.0) else 0.0
                                val catProgress = if (limit > 0.0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f
                                val isCatOver = spent > limit
                                val isCatWarning = (spent / limit) >= 0.8
                                
                                val catPercent = ((spent / limit) * 100).toInt()

                                val catColor = when {
                                    !balanceVisible -> GhostWhite.copy(alpha = 0.2f)
                                    isCatOver -> NeonRed
                                    isCatWarning -> Color(0xFFFFA000)
                                    else -> SteelBlue
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(TranslucentInput, RoundedCornerShape(12.dp))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(catColor, CircleShape)
                                            )
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = GhostWhite
                                            )
                                        }
                                        Text(
                                            text = if (balanceVisible) {
                                                if (isCatOver) "Over Rp ${FormatUtils.formatRupiahCompact(spent - limit)}" else "Sisa Rp ${FormatUtils.formatRupiahCompact(limit - spent)}"
                                            } else "Rp ••••••",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isCatOver && balanceVisible) NeonRed else GhostWhite.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(GhostWhite.copy(alpha = 0.05f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(catProgress)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(catColor)
                                                .testTag("category_budget_progress_fill_${cat.lowercase()}")
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (balanceVisible) {
                                                "${FormatUtils.formatRupiahCompact(spent)} terpakai dari ${FormatUtils.formatRupiahCompact(limit)}"
                                            } else "Rp •••••• terpakai",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GhostWhite.copy(alpha = 0.4f),
                                            fontSize = 10.sp
                                        )

                                        Text(
                                            text = if (balanceVisible) "$catPercent%" else "••%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = catColor,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRutinInfo) {
        AlertDialog(
            onDismissRequest = { showRutinInfo = false },
            title = { Text("Transaksi Rutin", color = GhostWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Transaksi Rutin memungkinkan Anda membuat transaksi yang otomatis tercatat setiap bulan tanpa perlu input manual.\n\nContoh penggunaan:\n• Gaji masuk setiap tanggal 1\n• Cicilan atau tagihan rutin setiap bulan\n• Uang jajan mingguan\n\nTransaksi akan otomatis ditambahkan ke catatan sesuai tanggal dan frekuensi yang Anda tentukan.",
                    color = GhostWhite.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showRutinInfo = false }) {
                    Text("Mengerti", color = SteelBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MidnightAbyss,
            titleContentColor = GhostWhite,
            textContentColor = GhostWhite
        )
    }
}

