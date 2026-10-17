package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.FinancialRecord
import com.example.ui.components.BezelCard
import com.example.ui.components.DashedDivider
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialPill
import com.example.ui.components.EditorialProgress
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.components.Stamp
import com.example.ui.components.Tag
import com.example.ui.config.FormatUtils
import com.example.ui.theme.*
import com.example.ui.util.CategoryIconMapper
import com.example.ui.util.displayCategoryName
import java.util.Locale

/**
 * Pemasukan memakai hijau hutan, pengeluaran memakai terakota: dua aksen yang
 * sama dengan referensi (--accent-2 dan --accent).
 */
val AmountIncomeColor = ForestGreen
val AmountExpenseColor = BurntOrange

/**
 * Ringkasan dashboard: satu kartu anggaran bulan berjalan yang sekaligus memuat
 * saldo total, pendapatan, dan pengeluaran bulan berjalan.
 *
 * Susunannya mengikuti "build slip" di referensi: label mono kecil di atas
 * angka serif besar, daftar fakta bergaris rambut, dan isi kartu yang dipisah
 * garis titik-titik.
 */
@Composable
fun DashboardStatsSection(
    totalIncome: Long,
    totalExpense: Long,
    currentBalance: Long,
    monthlySpendingTotal: Long,
    monthlyBudgetLimit: Long,
    categoryBudgets: Map<String, Long> = emptyMap(),
    // Tanpa default: peta ini wajib dikirim, kalau tidak label anggaran menampilkan
    // id mentah (mis. "cat:expense:game") dan pemakaian kategorinya selalu nol.
    categoryNameById: Map<String, String>,
    categorySpending: Map<String, Long> = emptyMap(),
    selectedBudgetOffset: Int = 0,
    onBudgetOffsetChange: (Int) -> Unit = {},
    onSetBudgetClick: () -> Unit,
    onDeleteBudgetClick: () -> Unit,
    onRecurringClick: () -> Unit,
    welcomeMessage: String = "",
    welcomeMessageColor: String = "",
    showNewFeature: Boolean = false,
    // Jangan pakai remember di sini: status saldo yang disembunyikan harus bertahan
    // saat aplikasi ditutup dan dibuka lagi, bukan reset tiap scope dibuat ulang.
    balanceVisible: Boolean,
    onToggleBalanceVisible: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(modifier = modifier.fillMaxWidth()) {
        if (welcomeMessage.isNotEmpty()) {
            val parsedColor = remember(welcomeMessageColor) {
                try {
                    Color(android.graphics.Color.parseColor(welcomeMessageColor))
                } catch (e: Exception) {
                    BurntOrange
                }
            }
            Text(
                text = welcomeMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = parsedColor,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }
        if (showNewFeature) {
            Tag(
                text = "Fitur baru tersedia",
                color = Amber,
                borderColor = Amber.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }

        // Saldo, pendapatan, dan pengeluaran tidak lagi berdiri sendiri di atas
        // kartu: ketiganya masuk ke dalam kartu anggaran bulanan sebagai kepala
        // "slip". Di luar kartu hanya tinggal pesan sambutan dan penanda fitur
        // baru, yang memang bukan bagian dari ringkasan bulan.
        BudgetSlip(
            monthlySpendingTotal = monthlySpendingTotal,
            monthlyBudgetLimit = monthlyBudgetLimit,
            balanceVisible = balanceVisible,
            balanceText = displayBalance,
            incomeText = displayIncome,
            expenseText = displayExpense,
            onToggleBalanceVisible = onToggleBalanceVisible,
            onRecurringClick = onRecurringClick,
            onRecurringInfoClick = { showRutinInfo = true },
            selectedBudgetOffset = selectedBudgetOffset,
            onBudgetOffsetChange = onBudgetOffsetChange,
            categoryBudgets = categoryBudgets,
            categoryNameById = categoryNameById,
            categorySpending = categorySpending,
            onSetBudgetClick = onSetBudgetClick,
            onDeleteBudgetClick = onDeleteBudgetClick
        )
    }

    if (showRutinInfo) {
        RutinInfoDialog(onDismiss = { showRutinInfo = false })
    }
}

/**
 * Satu kolom fakta: label mono kecil di atas nilainya (.hero-facts).
 *
 * [horizontalAlignment] dipakai kolom kanan supaya nominalnya nempel ke tepi
 * kartu, bukan menggantung di tengah antar-kolom.
 */
@Composable
private fun FactCell(
    label: String,
    value: String,
    valueColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier.testTag(testTag),
        horizontalAlignment = horizontalAlignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = valueColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Eyebrow(text = label)
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1
        )
    }
}

/**
 * Kartu anggaran bulan berjalan, sekaligus ringkasan halaman ini.
 *
 * Bentuknya mengikuti "build slip" di referensi: bagian saldo (saldo total,
 * pendapatan, pengeluaran) di atas, lalu bagian anggaran bulan yang dikendalikan
 * panah bulan, isi yang dipisah garis titik-titik, dan kaki berisi sisa dan
 * stempel persentase.
 *
 * Bagian saldo sengaja tidak ikut berubah saat bulan anggaran diganti: panah
 * bulan hanya menggeser batas belanja bulanan, sedangkan saldo total tetap
 * saldo berjalan sampai hari ini.
 */
@Composable
private fun BudgetSlip(
    monthlySpendingTotal: Long,
    monthlyBudgetLimit: Long,
    balanceVisible: Boolean,
    balanceText: String,
    incomeText: String,
    expenseText: String,
    onToggleBalanceVisible: () -> Unit,
    onRecurringClick: () -> Unit,
    onRecurringInfoClick: () -> Unit,
    selectedBudgetOffset: Int,
    onBudgetOffsetChange: (Int) -> Unit,
    categoryBudgets: Map<String, Long>,
    categoryNameById: Map<String, String>,
    categorySpending: Map<String, Long>,
    onSetBudgetClick: () -> Unit,
    onDeleteBudgetClick: () -> Unit
) {
    BezelCard {
        // ── Bagian saldo ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    text = "Saldo total",
                    modifier = Modifier.testTag("total_balance_label")
                )
                IconButton(
                    onClick = onToggleBalanceVisible,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("toggle_balance_visibility")
                ) {
                    Icon(
                        imageVector = if (balanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (balanceVisible) "Sembunyikan Saldo" else "Tampilkan Saldo",
                        tint = InkMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRecurringInfoClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Transaksi Rutin",
                        tint = InkMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                EditorialPill(
                    text = "Rutin",
                    selected = false,
                    onClick = onRecurringClick,
                    testTag = "recurring_transactions_inline_button"
                )
            }
        }

        // Angka saldo: serif tipis ala h1 hero di referensi. Ukurannya dipilih
        // menurut panjang nominal supaya nominal miliaran tidak terpotong --
        // Compose tidak mengecilkan font otomatis untuk satu baris.
        Text(
            text = balanceText,
            style = when {
                balanceText.length > 16 -> MaterialTheme.typography.headlineLarge
                balanceText.length > 13 -> MaterialTheme.typography.displaySmall
                else -> MaterialTheme.typography.displayLarge
            },
            color = Ink,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
        HairlineDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            FactCell(
                label = "Pendapatan",
                value = incomeText,
                valueColor = AmountIncomeColor,
                icon = Icons.Default.ArrowUpward,
                testTag = "total_income_card",
                modifier = Modifier.weight(1f)
            )
            FactCell(
                label = "Pengeluaran",
                value = expenseText,
                valueColor = AmountExpenseColor,
                icon = Icons.Default.ArrowDownward,
                testTag = "total_expense_card",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            )
        }

        // ── Bagian anggaran bulan berjalan ──────────────────────────────────
        HairlineDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onBudgetOffsetChange(selectedBudgetOffset - 1) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("prev_budget_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Bulan Sebelumnya",
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(text = "Anggaran bulan")
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = monthLabel(selectedBudgetOffset),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Medium,
                        color = BurntOrange,
                        modifier = Modifier.testTag("budget_current_month_label")
                    )
                    if (selectedBudgetOffset == 0) {
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Tag(text = "bulan ini")
                    }
                }
            }

            IconButton(
                onClick = { onBudgetOffsetChange(selectedBudgetOffset + 1) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("next_budget_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Bulan Selanjutnya",
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        HairlineDivider()
        Spacer(modifier = Modifier.height(Spacing.md))

        if (monthlyBudgetLimit <= 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Atur batas belanja bulanan Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                EditorialButton(
                    text = "Atur",
                    onClick = onSetBudgetClick,
                    // Tanpa chip panah: tombolnya cukup selebar label saja,
                    // tidak jadi pil besar di samping teks ajakan.
                    trailingArrow = false,
                    testTag = "set_budget_cta_button"
                )
            }
        } else {
            val progress = if (balanceVisible) {
                (monthlySpendingTotal.toDouble() / monthlyBudgetLimit).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            }
            val isOverBudget = monthlySpendingTotal > monthlyBudgetLimit
            val isWarningPercent = (monthlySpendingTotal.toDouble() / monthlyBudgetLimit) >= 0.8
            val progressColor = when {
                !balanceVisible -> HairlineStrong
                isOverBudget -> AmountExpenseColor
                isWarningPercent -> Amber
                else -> ForestGreen
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(progressColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Eyebrow(
                        text = if (isOverBudget) "Over budget" else "Belanja aman",
                        color = progressColor
                    )
                }
                // Pensil dan tong sampah jadi satu pasang, ukuran dan warnanya
                // sama supaya terbaca sebagai satu kelompok aksi.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSetBudgetClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("edit_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Anggaran",
                            tint = InkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteBudgetClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Anggaran",
                            tint = InkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            EditorialProgress(
                progress = progress,
                color = progressColor,
                testTag = "budget_progress_bar_fill"
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!balanceVisible) {
                            "Sisa: Rp ••••••"
                        } else if (isOverBudget) {
                            "Sisa: " + FormatUtils.formatRupiah(0L) + " (Over " + FormatUtils.formatRupiah(monthlySpendingTotal - monthlyBudgetLimit) + ")"
                        } else {
                            "Sisa: " + FormatUtils.formatRupiah(monthlyBudgetLimit - monthlySpendingTotal)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (balanceVisible) {
                            "${FormatUtils.formatRupiahCompact(monthlySpendingTotal)} terpakai dari ${FormatUtils.formatRupiahCompact(monthlyBudgetLimit)}"
                        } else {
                            "Rp •••••• terpakai dari Rp ••••••"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }
                val percentInt = ((monthlySpendingTotal.toDouble() / monthlyBudgetLimit) * 100).toInt()
                Stamp(
                    text = if (balanceVisible) "$percentInt%" else "••%",
                    color = progressColor
                )
            }

            val activeCategoryBudgets = categoryBudgets.filter { it.value > 0L }
            if (activeCategoryBudgets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                DashedDivider()
                Spacer(modifier = Modifier.height(Spacing.md))

                var isExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(ForestGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Eyebrow(text = "Anggaran per kategori")
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Sembunyikan" else "Tampilkan",
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    activeCategoryBudgets.entries.forEachIndexed { index, (catId, limit) ->
                        // Budget dikunci categoryId; label & spending pakai nama terbaru.
                        val cat = categoryNameById[catId] ?: catId
                        val spent = if (balanceVisible) (categorySpending[cat] ?: 0L) else 0L
                        val catProgress = if (limit > 0L) (spent.toDouble() / limit).coerceIn(0.0, 1.0).toFloat() else 0f
                        val isCatOver = spent > limit
                        val isCatWarning = (spent.toDouble() / limit) >= 0.8
                        val catPercent = ((spent.toDouble() / limit) * 100).toInt()
                        val catColor = when {
                            !balanceVisible -> HairlineStrong
                            isCatOver -> AmountExpenseColor
                            isCatWarning -> Amber
                            else -> ForestGreen
                        }

                        if (index > 0) {
                            DashedDivider()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(catColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink
                                    )
                                }
                                Text(
                                    text = if (balanceVisible) {
                                        // formatRupiahCompact sudah mem-prefix "Rp",
                                        // jadi di sini jangan ditambah lagi.
                                        if (isCatOver) {
                                            "Over ${FormatUtils.formatRupiahCompact(spent - limit)}"
                                        } else {
                                            "Sisa ${FormatUtils.formatRupiahCompact(limit - spent)}"
                                        }
                                    } else {
                                        "Rp ••••••"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCatOver && balanceVisible) AmountExpenseColor else InkMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))
                            EditorialProgress(
                                progress = catProgress,
                                color = catColor,
                                height = 6.dp,
                                testTag = "category_budget_progress_fill_${cat.lowercase(Locale.getDefault())}"
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (balanceVisible) {
                                        "${FormatUtils.formatRupiahCompact(spent)} terpakai dari ${FormatUtils.formatRupiahCompact(limit)}"
                                    } else {
                                        "Rp •••••• terpakai"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                                Text(
                                    text = if (balanceVisible) "$catPercent%" else "••%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = catColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "AGUSTUS 2026" untuk selisih bulan [offset] dari bulan berjalan. */
private fun monthLabel(offset: Int): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.MONTH, offset)
    val monthNames = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    return "${monthNames[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}".uppercase()
}

/** Dialog penjelasan transaksi rutin. */
@Composable
private fun RutinInfoDialog(onDismiss: () -> Unit) {
    EditorialDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadius.bezel))
                .background(CreamRaised)
                .border(1.dp, Hairline, RoundedCornerShape(CornerRadius.bezel))
                .padding(Spacing.xl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Eyebrow(text = "Transaksi rutin")
                Text(
                    text = "Catat otomatis tiap bulan",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink
                )
                Text(
                    text = "Transaksi Rutin memungkinkan Anda membuat transaksi yang otomatis tercatat setiap bulan tanpa perlu input manual.\n\nContoh penggunaan:\n• Gaji masuk setiap tanggal 1\n• Cicilan atau tagihan rutin setiap bulan\n• Uang jajan mingguan\n\nTransaksi akan otomatis ditambahkan ke catatan sesuai tanggal dan frekuensi yang Anda tentukan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                EditorialButton(
                    text = "Mengerti",
                    onClick = onDismiss,
                    // Tombol penutup: cukup selebar labelnya dan rata kanan,
                    // tidak melebar sampai tepi kiri dialog.
                    modifier = Modifier.align(Alignment.End),
                    testTag = "rutin_info_understand_button"
                )
            }
        }
    }
}

/**
 * Judul bagian riwayat plus pilihan filter.
 *
 * Ketiga pilihan dibagi rata selebar baris supaya labelnya benar-benar di tengah
 * dan "Pengeluaran" rata dengan tepi kanan. Padding dalam pil dikecilkan karena
 * "Pengeluaran" label terpanjang: dengan padding bawaan 14 dp labelnya turun
 * baris di layar 360 dp saat ikut dibesarkan.
 */
@Composable
fun DashboardHistoryFilter(
    currentFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Eyebrow(text = "Riwayat transaksi", color = Ink)
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_filter_dropdown"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Semua", "Pendapatan", "Pengeluaran").forEach { opt ->
                EditorialPill(
                    text = opt,
                    selected = currentFilter == opt,
                    onClick = { onFilterSelected(opt) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.labelLarge,
                    contentPadding = Spacing.sm
                )
            }
        }
    }
}

/** Kolom cari, tombol urut, dan tombol hapus semua. */
@Composable
fun DashboardSearchActions(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortByNewest: Boolean,
    onSortToggled: () -> Unit,
    onDeleteAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(CornerRadius.pill))
                .background(CreamRaised)
                .border(1.dp, Hairline, RoundedCornerShape(CornerRadius.pill))
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Cari",
                tint = InkMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Cari...",
                        color = InkMuted.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    cursorBrush = SolidColor(BurntOrange)
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus",
                        tint = InkMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(CornerRadius.pill))
                .border(1.dp, HairlineStrong, RoundedCornerShape(CornerRadius.pill))
                .clickable { onSortToggled() }
                .padding(horizontal = Spacing.lg)
                .testTag("history_sort_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = if (sortByNewest) "Terbaru" else "Terlama",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Icon(
                imageVector = if (sortByNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = "Urutkan",
                tint = Ink,
                modifier = Modifier.size(16.dp)
            )
        }

        // Tombol hapus semua transaksi. Ikon saja supaya tidak memakan lebar
        // baris cari; areanya tetap 48.dp agar mudah ditekan. Memakai aksen
        // terakota karena aksinya berbahaya; detail konfirmasinya ada di
        // DeleteAllRecordsDialog.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CornerRadius.pill))
                .background(BurntOrange.copy(alpha = 0.08f))
                .border(1.dp, BurntOrange.copy(alpha = 0.45f), RoundedCornerShape(CornerRadius.pill))
                .clickable { onDeleteAllClick() }
                .testTag("delete_all_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Hapus Semua Transaksi",
                tint = BurntOrange,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/** Satu baris transaksi: deskripsi di kiri, jumlah dan aksi di kanan. */
@Composable
fun DashboardTransactionRow(
    record: FinancialRecord,
    categoryNameById: Map<String, String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = record.type == "income"
    // Nama kategori me-resolve dari categoryId supaya rename ikut tercermin.
    val resolvedCategory = record.displayCategoryName(categoryNameById)
    val (parsedEmoji, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(resolvedCategory)
    val amountColor = if (isIncome) AmountIncomeColor else AmountExpenseColor

    // Huruf besar di awal tiap kata, sama seperti daftar transaksi di tab lain.
    val capitalizedDescription = record.description.trim().split(WHITESPACE_REGEX)
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase(Locale.getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md)
            .testTag("record_item_card_${record.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (parsedEmoji != null) "$parsedEmoji $capitalizedDescription" else capitalizedDescription,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cleanCategory,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm)
                        .size(3.dp)
                        .background(InkMuted.copy(alpha = 0.4f), CircleShape)
                )
                Text(
                    text = FormatUtils.formatDate(record.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            if (record.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Catatan: ${record.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isIncome) "+" + FormatUtils.formatRupiah(record.amount) else "-" + FormatUtils.formatRupiah(record.amount),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("edit_record_button"),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = InkMuted)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Transaksi", modifier = Modifier.size(15.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_record_button"),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = InkMuted)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus Transaksi", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/**
 * Dipakai untuk memecah kata pada judul transaksi.
 *
 * Sengaja dibuat sekali di level file: `String.toRegex()` menyusun ulang objek Pattern
 * setiap kali dipanggil, dan pemanggilnya ada di dalam item daftar yang terus
 * recomposition saat scroll. Menyusunnya per item terasa sebagai freeze saat digulir.
 */
private val WHITESPACE_REGEX = "\\s+".toRegex()

/** Keadaan kosong: belum ada transaksi, atau filter tidak menemukan apa pun. */
@Composable
fun DashboardEmptyState(
    isFiltered: Boolean,
    onSeedData: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isFiltered) Icons.Default.Search else Icons.Outlined.AddCircleOutline,
            contentDescription = null,
            tint = InkMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = if (isFiltered) "Tidak ada yang cocok" else "Belum ada transaksi",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = if (isFiltered) {
                "Coba ganti atau kosongkan filter di atas."
            } else {
                "Mulai catat pengeluaran pertamamu, atau isi dengan data contoh."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        if (isFiltered) {
            EditorialButton(
                text = "Hapus Filter",
                onClick = onClearFilters,
                style = EditorialButtonStyle.Ghost,
                testTag = "clear_filters_button"
            )
        } else {
            EditorialButton(
                text = "Isi Data Contoh",
                onClick = onSeedData,
                testTag = "seed_data_button"
            )
        }
    }
}
