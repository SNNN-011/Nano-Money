package com.example.ui.common

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.theme.*
import java.util.*

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

    EditorialDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Eyebrow(text = "Tanggal transaksi")
                Text(
                    text = String.format(
                        Locale.forLanguageTag("id-ID"),
                        "%02d %s %d",
                        selectedDay,
                        months[selectedMonth],
                        selectedYear
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = SerifFamily,
                    color = Ink
                )

                HairlineDivider()

                // Tiga pemilih sebaris, sama seperti susunan sebelumnya. Hanya
                // warnanya yang pindah ke palet editorial: label mono, angka
                // serif, panah tinta redup.
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

                    PickerColumn(
                        label = "Bulan",
                        valueText = months[selectedMonth].substring(0, 3),
                        onUp = { selectedMonth = if (selectedMonth < 11) selectedMonth + 1 else 0 },
                        onDown = { selectedMonth = if (selectedMonth > 0) selectedMonth - 1 else 11 }
                    )

                    NumberPickerColumn(
                        label = "Tahun",
                        value = selectedYear,
                        range = 2020..2035,
                        onValueChange = { selectedYear = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        accent = InkMuted
                    )
                    EditorialButton(
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
                        modifier = Modifier.weight(1f),
                        trailingArrow = false
                    )
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
    PickerColumn(
        label = label,
        valueText = value.toString(),
        onUp = { if (value < range.last) onValueChange(value + 1) },
        onDown = { if (value > range.first) onValueChange(value - 1) }
    )
}

/** Satu kolom pemilih: label mono, angka serif, dua panah redup. */
@Composable
private fun PickerColumn(
    label: String,
    valueText: String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(text = label)
        IconButton(onClick = onUp, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "$label naik",
                tint = InkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SerifFamily,
            color = Ink,
            modifier = Modifier.padding(vertical = Spacing.xs)
        )
        IconButton(onClick = onDown, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "$label turun",
                tint = InkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
