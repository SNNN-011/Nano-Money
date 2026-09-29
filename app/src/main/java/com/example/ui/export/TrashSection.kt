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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Red.copy(alpha = 0.35f),
                            Color.Red.copy(alpha = 0.05f)
                        )
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.background(TranslucentGlass)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Yakin Sekali lagi?",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ketik HAPUS untuk mengonfirmasi. $activeCount transaksi akan dipindahkan ke Sampah dan masih bisa dipulihkan.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )

                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { if (it.length <= 5) confirmText = it.uppercase() },
                            singleLine = true,
                            placeholder = {
                                Text("HAPUS", color = GhostWhite.copy(alpha = 0.3f), fontSize = 13.sp)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_all_confirm_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = GhostWhite,
                                fontWeight = FontWeight.Bold
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Red.copy(alpha = 0.7f),
                                unfocusedBorderColor = GhostWhite.copy(alpha = 0.15f),
                                cursorColor = Color.Red
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = onDismiss,
                                isActive = false,
                                modifier = Modifier.weight(1f),
                                testTag = "delete_all_step2_cancel"
                            )
                            PremiumButton(
                                text = "Hapus Semua",
                                onClick = {
                                    onDismiss()
                                    onConfirm()
                                },
                                // Nonaktif sampai kata kuncinya diketik benar.
                                enabled = confirmText == "HAPUS",
                                isActive = false,
                                gradientColors = listOf(
                                    Color.Red.copy(alpha = 0.85f),
                                    Color.Red.copy(alpha = 0.5f)
                                ),
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
 * Dialog konfirmasi dua tombol dengan gaya yang sama dipakai di tab Simpan
 * (MidnightAbyss + TranslucentGlass + border gradient + PremiumButton).
 *
 * Untuk aksi yang tidak bisa dibatalkan, gunakan isDestructive = true supaya
 * tombol konfirmasi tampil merah. Bila aksi sangat berisiko, tambahkan lagi
 * langkah kedua yang meminta pengguna mengetik kata kunci.
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDestructive) {
                        listOf(Color.Red.copy(alpha = 0.3f), Color.Red.copy(alpha = 0.05f))
                    } else {
                        listOf(GhostWhite.copy(alpha = 0.2f), GhostWhite.copy(alpha = 0.02f))
                    }
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.background(TranslucentGlass)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = title,
                        color = GhostWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = message,
                        color = GhostWhite.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumButton(
                            text = dismissText,
                            onClick = onDismiss,
                            isActive = false,
                            modifier = Modifier.weight(1f),
                            testTag = dismissTestTag
                        )
                        PremiumButton(
                            text = confirmText,
                            onClick = onConfirm,
                            isActive = !isDestructive,
                            gradientColors = if (isDestructive) {
                                listOf(Color.Red.copy(alpha = 0.85f), Color.Red.copy(alpha = 0.5f))
                            } else {
                                listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                            },
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
    activeCount: Int = 0,
    onRestore: (FinancialRecord) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showRestoreAllConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    

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
                // Dua tombol ini selalu bertumpuk. PremiumButton memakai maxLines = 1
                // tanpa overflow, jadi teks panjang seperti "PULIHKAN SEMUA" terpotong
                // di layar sempit. OutlinedButton membolehkan teks wrap ke dua baris.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showRestoreAllConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SteelBlue.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SteelBlue),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Pulihkan Semua",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { showEmptyConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.85f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Kosongkan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

            // Zona bahaya: hapus seluruh transaksi aktif. Bukan bagian dari Sampah,
            // tapi diletakkan di card yang sama karena ini soal keamanan data.
            if (activeCount > 0) {
                HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

                OutlinedButton(
                    onClick = { showDeleteAllConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("delete_all_records_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hapus Semua Transaksi ($activeCount)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Semua transaksi akan dipindahkan ke Sampah dan masih bisa dipulihkan.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = GhostWhite.copy(alpha = 0.4f)
                )
            }
        }
    }

    if (showRestoreAllConfirm) {
        AppConfirmDialog(
            title = "Pulihkan Semua Transaksi?",
            message = "${
                deletedRecords.size
            } transaksi akan dikembalikan ke daftar utama. Transaksi ini akan ikut tersinkron ke cloud.",
            confirmText = "Pulihkan",
            onConfirm = {
                showRestoreAllConfirm = false
                onRestoreAll()
            },
            onDismiss = { showRestoreAllConfirm = false },
            confirmTestTag = "confirm_restore_all_button",
            dismissTestTag = "cancel_restore_all_button"
        )
    }

    if (showDeleteAllConfirm) {
        DeleteAllRecordsDialog(
            activeCount = activeCount,
            onConfirm = {
                onDeleteAll()
                Toast.makeText(context, "Semua transaksi dipindahkan ke Sampah", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDeleteAllConfirm = false }
        )
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
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.testTag("restore_record_button_${record.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = null,
                tint = SteelBlue,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Pulihkan",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = SteelBlue,
                maxLines = 1
            )
        }
    }
}
