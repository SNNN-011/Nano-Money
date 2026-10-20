package com.example.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.config.FormatUtils
import com.example.ui.util.CategoryIconMapper
import com.example.ui.util.displayCategoryName
import java.util.*

/**
 * Dipakai untuk memecah kata pada judul transaksi.
 *
 * Sengaja dibuat sekali di level file: `String.toRegex()` menyusun ulang objek Pattern
 * setiap kali dipanggil, dan pemanggilnya ada di dalam item daftar yang terus
 * recomposition saat scroll. Menyusunnya per item terasa sebagai freeze saat digulir.
 */
private val WHITESPACE_REGEX = "\\s+".toRegex()

@Composable
fun TransactionListItem(
    record: FinancialRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    categoryNameById: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val isIncome = record.type == "income"
    // Nama kategori me-resolve dari categoryId supaya rename ikut tercermin.
    val resolvedCategory = record.displayCategoryName(categoryNameById)
    val (parsedEmoji, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(resolvedCategory)

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
        ForestGreen.copy(alpha = 0.12f)
    } else {
        BurntOrange.copy(alpha = 0.12f)
    }

    val badgeIconColor = if (isIncome) {
        ForestGreen
    } else {
        BurntOrange
    }

    // Capitalize each word in the title/description
    val capitalizedDescription = record.description.trim().split(WHITESPACE_REGEX)
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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = Ink,
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
                            color = InkMuted
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(3.dp)
                                .background(HairlineStrong, CircleShape)
                        )
                        Text(
                            text = FormatUtils.formatDate(record.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted.copy(alpha = 0.8f)
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
                            colors = IconButtonDefaults.iconButtonColors(contentColor = InkMuted.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Transaksi", modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("delete_record_button"),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = ErrorRed.copy(alpha = 0.8f))
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
                        color = InkMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
