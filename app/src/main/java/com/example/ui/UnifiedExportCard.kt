package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UnifiedExportCard(
    isWideScreen: Boolean,
    availableMonths: List<YearMonth>,
    selectedMonths: Set<YearMonth>,
    onSelectedMonthsChanged: (Set<YearMonth>) -> Unit,
    isMonthsExpanded: Boolean,
    onIsMonthsExpandedChanged: (Boolean) -> Unit,
    onExportPdfFile: (List<YearMonth>) -> Unit,
    onExportCsvFile: (List<YearMonth>) -> Unit,
    onImportCsvFile: () -> Unit
) {
    var showPdfHelpInfo by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header inside card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIsMonthsExpandedChanged(!isMonthsExpanded) }.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.4f))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pilih Periode Laporan Keuangan",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = GhostWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showPdfHelpInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Ekspor PDF",
                            tint = GhostWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isMonthsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isMonthsExpanded) "Sembunyikan" else "Tampilkan Semua",
                        tint = SteelBlue,
                        modifier = Modifier.size(20.dp).clickable { onIsMonthsExpandedChanged(!isMonthsExpanded) }
                    )
                }
            }

            if (showPdfHelpInfo) {
                AlertDialog(
                    onDismissRequest = { showPdfHelpInfo = false },
                    title = { Text("Informasi Ekspor PDF", color = GhostWhite, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "Ekspor laporan keuangan Anda dalam format PDF yang rapi dan profesional.\n\n• Pilih satu atau beberapa bulan sekaligus untuk dijadikan laporan.\n• Setiap bulan ditampilkan di halaman terpisah agar mudah dibaca.\n• Laporan mencakup ringkasan statistik, grafik arus kas, pie chart kategori pengeluaran, dan tabel transaksi lengkap.\n• File PDF langsung tersimpan di penyimpanan perangkat Anda dan bisa dibagikan kapan saja.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showPdfHelpInfo = false }) {
                            Text("Mengerti", color = SteelBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = MidnightAbyss,
                    titleContentColor = GhostWhite,
                    textContentColor = GhostWhite
                )
            }

            if (isMonthsExpanded) {
                Text(
                    text = "Tentukan bulan transaksi yang ingin di-ekspor atau di-cetak sebagai laporan keuangan. Anda bisa memilih satu, beberapa, atau semua bulan sekaligus.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = GhostWhite.copy(alpha = 0.55f)
                )

                // Month Selector Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumButton(
                        text = "Pilih Semua",
                        onClick = { onSelectedMonthsChanged(availableMonths.toSet()) },
                        isActive = true,
                        fillMaxWidth = false,
                        testTag = "select_all_months",
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp
                    )

                    PremiumButton(
                        text = "Kosongkan",
                        onClick = { onSelectedMonthsChanged(emptySet()) },
                        isActive = false,
                        fillMaxWidth = false,
                        testTag = "clear_all_months",
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp
                    )
                }

                // Render Selectable Months
                val chunkedMonths = availableMonths.chunked(if (isWideScreen) 3 else 2)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunkedMonths.forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            val emptySpaces = (if (isWideScreen) 3 else 2) - rowMonths.size
                            if (emptySpaces > 0) {
                                repeat(emptySpaces) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Collapsed state month summary indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIsMonthsExpandedChanged(true) }
                        .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID")) }
                    val displaySelectedStr = if (selectedMonths.isEmpty()) {
                        "Tidak ada bulan terpilih"
                    } else {
                        selectedMonths.sorted().joinToString(", ") { it.format(formatter) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Periode terpilih (${selectedMonths.size} bulan):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = SteelBlue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displaySelectedStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )
                    }

                    Text(
                        text = "Ubah",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SteelBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

            // Buttons Layout (PDF Wide, CSV side-by-side)
            PremiumButton(
                text = "UNDUH LAPORAN PDF",
                onClick = { onExportPdfFile(selectedMonths.toList()) },
                isActive = selectedMonths.isNotEmpty(),
                icon = Icons.Default.PictureAsPdf,
                testTag = "export_pdf_button",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumButton(
                    text = "Ekspor Excel",
                    onClick = { onExportCsvFile(selectedMonths.toList()) },
                    isActive = selectedMonths.isNotEmpty(),
                    icon = Icons.Default.Assessment,
                    testTag = "export_csv_button",
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp
                )

                PremiumButton(
                    text = "Impor Excel",
                    onClick = onImportCsvFile,
                    isActive = true,
                    icon = Icons.Default.Download,
                    testTag = "import_csv_button",
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp
                )
            }
        }
    }
}
