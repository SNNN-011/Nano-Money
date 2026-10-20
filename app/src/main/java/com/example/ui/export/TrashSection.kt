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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.FinancialRecord
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.components.Tag
import com.example.ui.config.FormatUtils
import com.example.ui.theme.*
import java.util.Locale

/**
 * Bagian Sampah di dalam tab Simpan.
 *
 * Penghapusan di app ini bersifat soft delete (isDeleted = 1), jadi transaksi
 * yang "dihapus" tidak benar-benar hilang dan masih bisa dipulihkan dari sini.
 */

/**
 * Dialog hapus semua transaksi dengan dua kali konfirmasi.
 *
 * Tahap 1: peringatan biasa. Tahap 2: pengguna harus mengetik HAPUS
 * sebelum tombol konfirmasi aktif. Dipakai dari Beranda (ikon sampah) dan
 * dari tab Simpan, sehingga logikanya tidak terduplikasi.
 *
 * Transaksi tidak dihapus permanen, melainkan dipindahkan ke Sampah.
 */
@Composable
fun DeleteAllRecordsDialog(
    activeCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var confirmText by remember { mutableStateOf("") }

    if (step == 0) {
        AppConfirmDialog(
            title = "Hapus Semua Transaksi?",
            message = "$activeCount transaksi akan dihapus dari daftar utama dan dipindahkan ke Sampah. Saldo dan statistik ikut ter-reset.",
            confirmText = "Lanjutkan",
            onConfirm = { step = 1 },
            onDismiss = onDismiss,
            confirmTestTag = "delete_all_step1_confirm",
            dismissTestTag = "delete_all_step1_cancel"
        )
    } else {
        EditorialDialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(CornerRadius.bezel),
                colors = CardDefaults.cardColors(containerColor = CreamRaised),
                border = BorderStroke(1.dp, Hairline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.background(CreamRaised)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Yakin Sekali lagi?",
                            color = Ink,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Ketik HAPUS untuk mengonfirmasi. $activeCount transaksi akan dipindahkan ke Sampah dan masih bisa dipulihkan.",
                            color = InkMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )

                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { if (it.length <= 5) confirmText = it.uppercase() },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "HAPUS",
                                    color = InkMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_all_confirm_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Ink,
                                fontWeight = FontWeight.Bold
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Ink,
                                unfocusedBorderColor = Hairline,
                                cursorColor = BurntOrange
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EditorialButton(
                                text = "Batal",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                style = EditorialButtonStyle.Ghost,
                                trailingArrow = false,
                                testTag = "delete_all_step2_cancel"
                            )
                            EditorialButton(
                                text = "Hapus Semua",
                                onClick = {
                                    onDismiss()
                                    onConfirm()
                                },
                                // Nonaktif sampai kata kuncinya diketik benar.
                                enabled = confirmText == "HAPUS",
                                // Aksi merusak memakai aksen jingga, sama dengan
                                // nominal pengeluaran di Dashboard.
                                accent = BurntOrange,
                                trailingArrow = false,
                                modifier = Modifier.weight(1.2f),
                                testTag = "delete_all_step2_confirm"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog konfirmasi dua tombol dengan palet editorial yang sama dengan tab
 * Dashboard (krem + garis rambut + sudut bezel + tombol pil).
 *
 * Untuk aksi yang tidak bisa dibatalkan, gunakan isDestructive = true supaya
 * tombol konfirmasi memakai aksen jingga. Bila aksi sangat berisiko, tambahkan
 * lagi langkah kedua yang meminta pengguna mengetik kata kunci.
 */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "Batal",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    confirmTestTag: String = "",
    dismissTestTag: String = ""
) {
    EditorialDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.background(CreamRaised)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        color = Ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = message,
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditorialButton(
                            text = dismissText,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            style = EditorialButtonStyle.Ghost,
                            trailingArrow = false,
                            testTag = dismissTestTag
                        )
                        EditorialButton(
                            text = confirmText,
                            onClick = onConfirm,
                            // Aksen jingga untuk aksi merusak, tinta untuk yang aman.
                            accent = if (isDestructive) BurntOrange else Ink,
                            trailingArrow = false,
                            modifier = Modifier.weight(1.2f),
                            testTag = confirmTestTag
                        )
                    }
                }
            }
        }
    }
}

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
    var showRestoreAllConfirm by remember { mutableStateOf(false) }
    

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trash_card"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Eyebrow(text = "Pemulihan")
                if (deletedRecords.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Tag(text = "${deletedRecords.size}", color = BurntOrange, borderColor = BurntOrange.copy(alpha = 0.4f))
                }
            }
            Text(
                text = "Sampah",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SerifFamily,
                color = Ink
            )
            Text(
                text = if (deletedRecords.isEmpty()) {
                    "Transaksi yang dihapus akan tersimpan di sini dan bisa dipulihkan."
                } else {
                    "Transaksi yang sudah dihapus belum hilang permanen. Pilih untuk mengembalikannya."
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }

            if (deletedRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = HairlineStrong,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Sampah kosong",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
            } else {
                // Dua tombol ini selalu bertumpuk selebar baris, sama seperti
                // aksi lain di tab Simpan.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EditorialButton(
                        text = "Pulihkan semua",
                        onClick = { showRestoreAllConfirm = true },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false
                    )
                    EditorialButton(
                        text = "Kosongkan",
                        onClick = { showEmptyConfirm = true },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        accent = BurntOrange,
                        trailingArrow = false
                    )
                }

                HairlineDivider()

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

    if (showEmptyConfirm) {
        AppConfirmDialog(
            title = "Kosongkan Sampah?",
            message = "${
                deletedRecords.size
            } transaksi akan dihapus permanen dan tidak bisa dipulihkan lagi. Tindakan ini tidak bisa dibatalkan.",
            confirmText = "Hapus",
            onConfirm = {
                showEmptyConfirm = false
                onEmptyTrash()
                Toast.makeText(context, "Sampah dikosongkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showEmptyConfirm = false },
            isDestructive = true,
            confirmTestTag = "confirm_empty_trash_button",
            dismissTestTag = "cancel_empty_trash_button"
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
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = Ink,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isIncome) "+" else "-",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = if (isIncome) ForestGreen else BurntOrange
                )
                Text(
                    text = FormatUtils.formatRupiah(record.amount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = if (isIncome) ForestGreen else BurntOrange
                )
            }
        }

        TextButton(
            onClick = onRestore,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
            modifier = Modifier.testTag("restore_record_button_${record.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "Pulihkan",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = Ink,
                maxLines = 1
            )
        }
    }
}
