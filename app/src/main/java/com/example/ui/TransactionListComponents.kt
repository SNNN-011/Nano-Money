package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.util.CategoryIconMapper
import java.util.*

@Composable
fun FilterAndSortHeader(
    currentFilter: String,
    isNewest: Boolean,
    onFilterSelected: (String) -> Unit,
    onSortToggled: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .background(
                            color = GhostWhite,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RIWAYAT TRANSAKSI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = GhostWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Categories toggles row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("history_filter_dropdown"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions = listOf("Semua", "Pendapatan", "Pengeluaran")
            filterOptions.forEach { opt ->
                val isSelected = currentFilter == opt
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onFilterSelected(opt) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = opt,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .fillMaxWidth(0.35f)
                                .background(
                                    color = if (isSelected) GhostWhite else Color.Transparent,
                                    shape = RoundedCornerShape(1.5.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionList(
    records: List<FinancialRecord>,
    onEdit: (FinancialRecord) -> Unit,
    onDelete: (FinancialRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStatePlaceholder()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(records.size, key = { records[it].id }) { index ->
                TransactionListItem(
                    record = records[index],
                    onEdit = { onEdit(records[index]) },
                    onDelete = { onDelete(records[index]) }
                )
                if (index < records.lastIndex) {
                    HorizontalDivider(
                        color = GhostWhite.copy(alpha = 0.05f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(
    record: FinancialRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = record.type == "income"
    val (parsedEmoji, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(record.category)

    // Dynamic contextual category icon identification
    val icon = when (cleanCategory.lowercase(Locale.getDefault()).trim()) {
        "makanan", "kuliner", "makan", "makan siang", "food", "restaurant" -> Icons.Default.Restaurant
        "transportasi", "transport", "travel", "grab", "gojek", "ojek", "mobil", "motor" -> Icons.Default.DirectionsCar
        "gaji", "investasi", "bonus", "gaji bulanan", "pendapatan", "income", "payments" -> Icons.Default.Payments
        "belanja", "shopping", "keperluan" -> Icons.Default.ShoppingBag
        "utilitas", "tagihan", "listrik", "air", "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
        "hiburan", "entertainment", "rekreasi", "nonton", "game" -> Icons.Default.TheaterComedy
        else -> if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    }

    // Dynamic coloring
    val badgeBgColor = if (isIncome) {
        NeonGreen.copy(alpha = 0.1f)
    } else {
        NeonRed.copy(alpha = 0.1f)
    }

    val badgeIconColor = if (isIncome) {
        NeonGreen
    } else {
        NeonRed
    }

    // Capitalize each word in the title/description
    val capitalizedDescription = record.description.trim().split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("record_item_card_${record.id}")
            .background(Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Type Indicator icon decoration - perfect smaller simple circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = badgeBgColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (parsedEmoji != null) {
                    Text(
                        text = parsedEmoji,
                        fontSize = 18.sp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = cleanCategory,
                        tint = badgeIconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body content description
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = capitalizedDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = GhostWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIncome) "+" + FormatUtils.formatRupiah(record.amount) else "-" + FormatUtils.formatRupiah(record.amount),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        ),
                        color = badgeIconColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category badge & date info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cleanCategory,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = GhostWhite.copy(alpha = 0.6f)
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(3.dp)
                                .background(GhostWhite.copy(alpha = 0.3f), CircleShape)
                        )
                        Text(
                            text = FormatUtils.formatDate(record.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = GhostWhite.copy(alpha = 0.4f)
                        )
                    }

                    // Operation icons buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("edit_record_button"),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = GhostWhite.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Transaksi", modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("delete_record_button"),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = GhostWhite.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Transaksi", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (record.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Catatan: ${record.notes}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = GhostWhite.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStatePlaceholder(onSeedData: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = TranslucentBorder,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = GhostWhite,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Belum Ada Transaksi",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            ),
            color = GhostWhite
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ketuk tab Tambah di menu bawah untuk mencatat transaksi baru Anda.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = GhostWhite.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
        if (onSeedData != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PremiumButton(
                text = "Gunakan Data Sampel",
                onClick = onSeedData,
                isActive = true,
                testTag = "seed_data_button",
                fillMaxWidth = false
            )
        }
    }
}
