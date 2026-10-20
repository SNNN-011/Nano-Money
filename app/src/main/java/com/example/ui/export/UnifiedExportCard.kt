package com.example.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialDialogCard
import com.example.ui.components.EditorialPill
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.theme.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Satu bulan pada pemilih periode; bentuknya pil yang sama dengan filter lain. */
@Composable
fun MonthSelectChip(
    month: YearMonth,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // "MMM" (Okt 2026) supaya label muat di pil selebar separuh baris.
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID")) }
    val formattedName = remember(month) { month.format(formatter) }

    EditorialPill(
        text = formattedName,
        selected = isSelected,
        onClick = onClick,
        modifier = modifier,
        textStyle = MaterialTheme.typography.labelMedium,
        contentPadding = Spacing.md,
        testTag = "month_chip_${month.year}_${month.monthValue}"
    )
}

/**
 * Pemilih periode laporan + tombol ekspor/impor.
 *
 * Kolom periode tetap di tempat; mengetuknya membuka menu pilihan bulan, jadi
 * tata letak halaman tidak melompat seperti versi yang melebar di tempat.
 */
@Composable
fun UnifiedExportCard(
    isWideScreen: Boolean,
    availableMonths: List<YearMonth>,
    selectedMonths: Set<YearMonth>,
    onSelectedMonthsChanged: (Set<YearMonth>) -> Unit,
    onExportPdfFile: (List<YearMonth>) -> Unit,
    onExportCsvFile: (List<YearMonth>) -> Unit,
    onImportCsvFile: () -> Unit
) {
    var showPdfHelpInfo by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Eyebrow(text = "Ekspor & impor")

            IconButton(
                onClick = { showPdfHelpInfo = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info ekspor laporan",
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = "Pilih periode laporan",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SerifFamily,
            color = Ink
        )

        // Ringkasan periode = kolom isian ketuk. Yang terbuka adalah menu,
        // bukan isi halaman yang berubah.
        val shape = RoundedCornerShape(CornerRadius.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CreamRaised)
                .border(1.dp, HairlineStrong, shape)
                .clickable { showMonthPicker = true }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .testTag("month_picker_field"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID")) }
            val displaySelectedStr = if (selectedMonths.isEmpty()) {
                "Tidak ada bulan terpilih"
            } else {
                selectedMonths.sorted().joinToString(", ") { it.format(formatter) }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Periode terpilih (${selectedMonths.size} bulan)",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Text(
                    text = displaySelectedStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        HairlineDivider()

        EditorialButton(
            text = "Unduh laporan PDF",
            onClick = { onExportPdfFile(selectedMonths.toList()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedMonths.isNotEmpty(),
            trailingArrow = false,
            expanded = true,
            testTag = "export_pdf_button"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            EditorialButton(
                text = "Ekspor Excel",
                onClick = { onExportCsvFile(selectedMonths.toList()) },
                modifier = Modifier.weight(1f),
                style = EditorialButtonStyle.Ghost,
                trailingArrow = false,
                enabled = selectedMonths.isNotEmpty(),
                testTag = "export_csv_button"
            )
            EditorialButton(
                text = "Impor Excel",
                onClick = onImportCsvFile,
                modifier = Modifier.weight(1f),
                style = EditorialButtonStyle.Ghost,
                trailingArrow = false,
                testTag = "import_csv_button"
            )
        }
    }

    if (showPdfHelpInfo) {
        EditorialDialog(onDismissRequest = { showPdfHelpInfo = false }) {
            EditorialDialogCard(title = "Informasi ekspor laporan") {
                Text(
                    text = "Pilih bulan lewat kolom periode. Bisa satu atau beberapa bulan.\n\n" +
                        "PDF: setiap bulan jadi halaman terpisah, berisi ringkasan statistik, grafik arus kas, pie chart kategori pengeluaran, dan tabel transaksi. File langsung tersimpan di perangkat dan bisa dibagikan kapan saja.\n\n" +
                        "Excel: data mentah transaksi pada bulan terpilih, bisa dibuka di aplikasi lembar kerja.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                EditorialButton(
                    text = "Mengerti",
                    onClick = { showPdfHelpInfo = false },
                    modifier = Modifier.fillMaxWidth(),
                    trailingArrow = false,
                    expanded = true,
                    testTag = "pdf_info_dismiss"
                )
            }
        }
    }

    if (showMonthPicker) {
        EditorialDialog(onDismissRequest = { showMonthPicker = false }) {
            EditorialDialogCard(title = "Pilih periode laporan") {
                Text(
                    text = "${selectedMonths.size} dari ${availableMonths.size} bulan terpilih",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    modifier = Modifier.testTag("month_picker_count")
                )

                // Tinggi dibatasi supaya daftar bulan bertahun-tahun tetap muat.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    val columns = if (isWideScreen) 3 else 2
                    availableMonths.chunked(columns).forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            rowMonths.forEach { month ->
                                MonthSelectChip(
                                    month = month,
                                    isSelected = selectedMonths.contains(month),
                                    onClick = {
                                        val updated = if (selectedMonths.contains(month)) {
                                            selectedMonths - month
                                        } else {
                                            selectedMonths + month
                                        }
                                        onSelectedMonthsChanged(updated)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowMonths.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Pilih semua",
                        onClick = { onSelectedMonthsChanged(availableMonths.toSet()) },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        testTag = "select_all_months"
                    )
                    EditorialButton(
                        text = "Kosongkan",
                        onClick = { onSelectedMonthsChanged(emptySet()) },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        testTag = "clear_all_months"
                    )
                }

                EditorialButton(
                    text = "Selesai",
                    onClick = { showMonthPicker = false },
                    modifier = Modifier.fillMaxWidth(),
                    trailingArrow = false,
                    expanded = true,
                    testTag = "month_picker_done"
                )
            }
        }
    }
}

/**
 * Konfirmasi sebelum impor Excel/CSV dijalankan.
 *
 * Impor lama langsung mengganti seluruh data (hard delete), jadi user harus memilih
 * mode dulu: "Gabung" hanya menambah baris baru, "Ganti Semua" memindahkan transaksi
 * aktif ke Sampah (masih bisa dipulihkan) sebelum baris impor dimasukkan.
 *
 * [rowCount] = jumlah baris valid di file, [activeCount] = jumlah transaksi aktif saat ini.
 */
@Composable
fun ImportCsvConfirmDialog(
    rowCount: Int,
    activeCount: Int,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    EditorialDialog(onDismissRequest = onDismiss) {
        EditorialDialogCard(title = "Impor data Excel/CSV") {
            Text(
                text = "$rowCount baris siap diimpor. Saat ini ada $activeCount transaksi aktif.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink
            )
            Text(
                text = "Gabung (aman): $rowCount baris ditambahkan ke data yang ada. Data lama tidak diubah.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                lineHeight = 18.sp
            )
            Text(
                text = "Ganti semua: $activeCount transaksi aktif dipindah ke Sampah (masih bisa dipulihkan), lalu $rowCount baris dari file dimasukkan.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            EditorialButton(
                text = "Gabung (aman)",
                onClick = onMerge,
                modifier = Modifier.fillMaxWidth(),
                trailingArrow = false,
                expanded = true,
                testTag = "import_confirm_merge"
            )
            EditorialButton(
                text = "Ganti semua",
                onClick = onReplace,
                modifier = Modifier.fillMaxWidth(),
                style = EditorialButtonStyle.Ghost,
                // Aksi paling berisiko di dialog ini: aksen jingga.
                accent = BurntOrange,
                trailingArrow = false,
                expanded = true,
                testTag = "import_confirm_replace"
            )
            EditorialButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                style = EditorialButtonStyle.Ghost,
                trailingArrow = false,
                accent = InkMuted,
                expanded = true,
                testTag = "import_confirm_cancel"
            )
        }
    }
}
