package com.example.ui.transaction

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.PremiumButton
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.MidnightAbyss
import com.example.ui.theme.SteelBlue
import com.example.ui.config.FormatUtils
import java.util.Locale

/**
 * Sampah: transaksi yang sudah dihapus tapi masih bisa dipulihkan.
 * Penghapusan di app ini bersifat soft delete (isDeleted = 1), jadi
 * datanya masih ada di database sampai sampah dikosongkan.
 */
@Composable
fun TrashTab(
    deletedRecords: List<FinancialRecord>,
    onRestore: (FinancialRecord) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sampah",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = GhostWhite
        )
        Text(
            text = if (deletedRecords.isEmpty()) {
                "Transaksi yang dihapus akan tersimpan di sini dan bisa dipulihkan."
            } else {
                "${deletedRecords.size} transaksi menunggu dipulihkan."
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = GhostWhite.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (deletedRecords.isNotEmpty()) {
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (deletedRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = GhostWhite.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sampah kosong",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.35f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
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
