package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import java.util.*

@Composable
fun BackupManualSection(
    showPasteSection: Boolean,
    pasteText: String,
    onShowToggled: () -> Unit,
    onPasteTextChanged: (String) -> Unit,
    onImportText: () -> Unit,
    onCopyFullData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
        border = BorderStroke(1.dp, TranslucentBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestorePage,
                        contentDescription = null,
                        tint = GhostWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transfer Data Manual (JSON)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = GhostWhite
                    )
                }

                IconButton(onClick = onShowToggled) {
                    Icon(
                        imageVector = if (showPasteSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka Cadangan",
                        tint = GhostWhite
                    )
                }
            }

            AnimatedVisibility(visible = showPasteSection) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Gunakan fitur ini untuk mentransfer data keuangan antar perangkat dengan menyalin teks JSON di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.5f)
                    )

                    PremiumButton(
                        text = "Salin Data ke Clipboard",
                        onClick = onCopyFullData,
                        modifier = Modifier.fillMaxWidth(),
                        isActive = true,
                        icon = Icons.Default.Share
                    )

                    HorizontalDivider(color = TranslucentBorder, thickness = 1.dp)

                    Text(
                        text = "Tempel teks cadangan JSON di bawah untuk memuat data:",
                        style = MaterialTheme.typography.labelSmall,
                        color = GhostWhite.copy(alpha = 0.5f)
                    )

                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = onPasteTextChanged,
                        placeholder = { Text("Tempel teks [JSON Array] di sini...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(6.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedLabelColor = GhostWhite,
                            unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f),
                            focusedBorderColor = GhostWhite,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    PremiumButton(
                        text = "Mulai Impor Teks",
                        onClick = { if (pasteText.trim().isNotEmpty()) onImportText() },
                        modifier = Modifier.fillMaxWidth(),
                        isActive = pasteText.trim().isNotEmpty(),
                        icon = Icons.Default.Check
                    )
                }
            }
        }
    }
}

@Composable
fun CustomDatePickerDialog(
    initialTimeMs: Long,
    onDateSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initialTimeMs } }
    var selectedDay by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }

    val months = remember {
        listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.2f),
                        GhostWhite.copy(alpha = 0.02f)
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Pilih Tanggal Transaksi",
                        style = MaterialTheme.typography.titleLarge,
                        color = GhostWhite,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = String.format(Locale.forLanguageTag("id-ID"), "%02d %s %d", selectedDay, months[selectedMonth], selectedYear),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SteelBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NumberPickerColumn(
                            label = "Hari",
                            value = selectedDay,
                            range = 1..31,
                            onValueChange = { selectedDay = it }
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bulan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            IconButton(onClick = { if (selectedMonth < 11) selectedMonth++ else selectedMonth = 0 }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Bulan berikutnya", tint = GhostWhite)
                            }
                            Text(
                                text = months[selectedMonth].substring(0, 3),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GhostWhite,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(onClick = { if (selectedMonth > 0) selectedMonth-- else selectedMonth = 11 }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bulan sebelumnya", tint = GhostWhite)
                            }
                        }

                        NumberPickerColumn(
                            label = "Tahun",
                            value = selectedYear,
                            range = 2020..2035,
                            onValueChange = { selectedYear = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumButton(
                            text = "Batal",
                            onClick = onDismissRequest,
                            isActive = false,
                            modifier = Modifier.weight(1f)
                        )
                        PremiumButton(
                            text = "Pilih",
                            onClick = {
                                val outCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, selectedYear)
                                    set(Calendar.MONTH, selectedMonth)
                                    val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                                    set(Calendar.DAY_OF_MONTH, selectedDay.coerceAtMost(maxDay))
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onDateSelected(outCal.timeInMillis)
                            },
                            isActive = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NumberPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Tambah", tint = GhostWhite)
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GhostWhite,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Kurangi", tint = GhostWhite)
        }
    }
}
