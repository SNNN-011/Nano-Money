package com.example.ui.export

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.config.FormatUtils
import com.example.ui.theme.*
import java.util.Locale

/**
 * Bagian Sampah di dalam tab Simpan.
 *
 * Penghapusan di app ini bersifat soft delete (isDeleted = 1), jadi transaksi
 * yang "dihapus" tidak benar-benar hilang dan masih bisa dipulihkan dari sini.
 */
@Composable
fun TrashSection(
    deletedRecords: List<FinancialRecord>,
    onRestore: (FinancialRecord) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trash_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Red.copy(alpha = 0.8f), Color.Red.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sampah",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = GhostWhite
                )
                if (deletedRecords.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${deletedRecords.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = Color.Red.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Text(
                text = if (deletedRecords.isEmpty()) {
                    "Transaksi yang dihapus akan tersimpan di sini dan bisa dipulihkan."
                } else {
                    "Transaksi yang sudah dihapus belum hilang permanen. Pilih untuk mengembalikannya."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = GhostWhite.copy(alpha = 0.55f)
            )

            if (deletedRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = GhostWhite.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sampah kosong",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PremiumButton(
                        text = "PULIHKAN SEMUA",
                        onClick = onRestoreAll,
                        isActive = true,
                        icon = Icons.Default.Restore,
                        fillMaxWidth = false,
                        horizontalPadding = 12.dp,
                        verticalPadding = 8.dp,
                        modifier = Modifier.weight(1f)
                    )
                    PremiumButton(
                        text = "KOSONGKAN",
                        onClick = { showEmptyConfirm = true },
                        isActive = false,
                        icon = Icons.Default.DeleteForever,
                        fillMaxWidth = false,
                        horizontalPadding = 12.dp,
                        verticalPadding = 8.dp,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

                // Batasi tinggi supaya tidak memakan seluruh tab; daftar tetap bisa di-scroll.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(deletedRecords, key = { it.id }) { record ->
                        TrashItem(
                            record = record,
                            onRestore = { onRestore(record) }
                        )
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            containerColor = MidnightAbyss,
            title = {
                Text(text = "Kosongkan Sampah?", color = GhostWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "${deletedRecords.size} transaksi akan dihapus permanen dan tidak bisa dipulihkan lagi. Tindakan ini tidak bisa dibatalkan.",
                    color = GhostWhite.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyConfirm = false
                        onEmptyTrash()
                        Toast.makeText(context, "Sampah dikosongkan", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "Hapus Permanen", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(text = "Batal", color = GhostWhite.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun TrashItem(
    record: FinancialRecord,
    onRestore: () -> Unit
) {
    val isIncome = record.type == "income"
    val formatted = record.description.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(10.dp))
            .border(1.dp, GhostWhite.copy(alpha = 0.06f), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = GhostWhite.copy(alpha = 0.75f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isIncome) "+" else "-",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = if (isIncome) Color.Green.copy(alpha = 0.7f) else Color.Red.copy(alpha = 0.7f)
                )
                Text(
                    text = FormatUtils.formatRupiah(record.amount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = if (isIncome) Color.Green.copy(alpha = 0.7f) else Color.Red.copy(alpha = 0.7f)
                )
            }
        }

        TextButton(
            onClick = onRestore,
            modifier = Modifier.testTag("restore_record_button_${record.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = "Pulihkan",
                tint = SteelBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "PULIHKAN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = SteelBlue
            )
        }
    }
}
