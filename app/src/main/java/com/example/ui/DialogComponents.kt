package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import java.util.*

@Composable
fun MessageAlertDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SteelBlue,
                        modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    PremiumButton(
                        text = "OK",
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                        isActive = true,
                        testTag = "message_dialog_ok"
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteTransactionDialog(
    record: FinancialRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                )
                Text(
                    text = "Hapus Transaksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GhostWhite,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Apakah Anda yakin ingin menghapus transaksi \"${record.description}\" senilai ${FormatUtils.formatRupiah(record.amount)}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PremiumButton(
                        text = "Batal",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        isActive = false,
                        testTag = "cancel_delete_button"
                    )
                    PremiumButton(
                        text = "Hapus",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isActive = true,
                        testTag = "confirm_delete_button"
                    )
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    formType: String,
    currentCategories: List<String>,
    onDismissRequest: () -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Boolean
) {
    var newCategoryName by remember { mutableStateOf("") }
    var categoryNameError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Kelola Kategori ${if (formType == "income") "Pendapatan" else "Pengeluaran"}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = GhostWhite
                    )

                    Text(
                        text = "Tambah atau hapus kategori kustom sesuai dengan preferensi Anda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )

                    // Add Category Input Form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = {
                                newCategoryName = it
                                categoryNameError = null
                            },
                            placeholder = { Text("Nama Kategori Baru", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            isError = categoryNameError != null,
                            modifier = Modifier.weight(1f).testTag("new_category_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GhostWhite,
                                unfocusedTextColor = GhostWhite,
                                focusedLabelColor = GhostWhite,
                                unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                focusedContainerColor = GhostWhite.copy(alpha = 0.05f),
                                unfocusedContainerColor = GhostWhite.copy(alpha = 0.02f),
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorTextColor = GhostWhite
                            )
                        )
                        Button(
                            onClick = {
                                val trimmed = newCategoryName.trim()
                                if (trimmed.isEmpty()) {
                                    categoryNameError = "Nama tidak boleh kosong"
                                } else if (currentCategories.contains(trimmed)) {
                                    categoryNameError = "Kategori sudah ada"
                                } else {
                                    onAddCategory(trimmed)
                                    newCategoryName = ""
                                    categoryNameError = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(52.dp).testTag("add_category_confirm_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(24.dp))
                        }
                    }
                    if (categoryNameError != null) {
                        Text(
                            text = categoryNameError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TranslucentBorder)
                    )

                    Text(
                        text = "Daftar Kategori Saat Ini",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = GhostWhite.copy(alpha = 0.8f)
                    )

                    // Categories List inside Scrollable column
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentCategories.forEach { cat ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = TranslucentInput
                                ),
                                border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = if (formType == "income") Color(0xFF4CAF50) else Color(0xFFF44336),
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GhostWhite
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (currentCategories.size <= 1) {
                                                categoryNameError = "Tidak bisa menghapus kategori terakhir!"
                                            } else {
                                                val deleted = onDeleteCategory(cat)
                                                if (!deleted) {
                                                    categoryNameError = "Gagal menghapus kategori!"
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_category_button_${cat.lowercase(Locale.ROOT)}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Kategori $cat",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        PremiumButton(
                            text = "Selesai",
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("close_category_dialog_button"),
                            isActive = true,
                            fillMaxWidth = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetDialog(
    currentBudgetLimit: Double,
    categoryBudgets: Map<String, Double>,
    expenseCategories: List<String>,
    onConfirm: (Double, Map<String, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetInput by remember {
        mutableStateOf(
            if (currentBudgetLimit > 0.0) {
                if (currentBudgetLimit % 1.0 == 0.0) currentBudgetLimit.toLong().toString() else currentBudgetLimit.toString()
            } else ""
        )
    }
    var budgetError by remember { mutableStateOf<String?>(null) }

    // Map input states for category budgets in a mutable map
    val localCategoryBudgets = remember {
        mutableStateMapOf<String, String>().apply {
            expenseCategories.forEach { cat ->
                val value = categoryBudgets[cat]
                put(cat, if (value != null && value > 0.0) {
                    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
                } else "")
            }
        }
    }

    val selectedEnabledCategories = remember {
        mutableStateListOf<String>().apply {
            expenseCategories.forEach { cat ->
                val value = categoryBudgets[cat]
                if (value != null && value > 0.0) {
                    add(cat)
                }
            }
        }
    }

    var showCategoryPickerPopup by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMonthlyBudgetInfo by remember { mutableStateOf(false) }
    var showCategoryBudgetInfo by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Atur Anggaran Bulanan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = GhostWhite,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showMonthlyBudgetInfo = !showMonthlyBudgetInfo },
                    modifier = Modifier.size(28.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Informasi Anggaran Bulanan",
                        tint = SteelBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showMonthlyBudgetInfo) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteelBlue.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SteelBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mengenai Anggaran Bulanan",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = SteelBlue
                            )
                            Text(
                                text = "Batas Anggaran Bulanan menetapkan batas pengeluaran maksimum Anda untuk seluruh pengeluaran dalam satu bulan berjalan. Aplikasi ini membantu mengontrol pengeluaran agar Anda tidak boros dan tetap hemat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Tentukan batas pengeluaran maksimum Anda untuk bulan ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )

                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = {
                        budgetInput = it
                        budgetError = null
                    },
                    label = { Text("Batas Anggaran (Rupiah)", style = MaterialTheme.typography.bodyMedium) },
                    placeholder = { Text("Misal: 5000000", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    isError = budgetError != null,
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        focusedLabelColor = GhostWhite,
                        unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                        focusedContainerColor = GhostWhite.copy(alpha = 0.05f),
                        unfocusedContainerColor = GhostWhite.copy(alpha = 0.02f),
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        errorTextColor = GhostWhite
                    )
                )

                if (budgetError != null) {
                    Text(
                        text = budgetError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // Preset options for better user experience
                Text(
                    text = "Pilih Cepat",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = GhostWhite.copy(alpha = 0.8f)
                )

                val presets = listOf(
                    1_000_000.0 to "1 Juta",
                    2_000_000.0 to "2 Juta",
                    5_000_000.0 to "5 Juta",
                    10_000_000.0 to "10 Juta"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (value, label) ->
                        val isSelected = budgetInput == value.toLong().toString()
                        val containerCol = if (isSelected) SteelBlue.copy(alpha = 0.2f) else TranslucentInput
                        val borderCol = if (isSelected) SteelBlue else GhostWhite.copy(alpha = 0.1f)
                        val textCol = if (isSelected) SteelBlue else GhostWhite.copy(alpha = 0.8f)

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    budgetInput = value.toLong().toString()
                                    budgetError = null
                                }
                                .testTag("budget_preset_${value.toLong()}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = containerCol),
                            border = BorderStroke(1.dp, borderCol)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = textCol
                                )
                            }
                        }
                    }
                }

                // Section for category budget targeting
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Batas Anggaran Per Kategori (Opsional)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = GhostWhite.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showCategoryBudgetInfo = !showCategoryBudgetInfo },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Informasi Anggaran Kategori",
                            tint = SteelBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showCategoryBudgetInfo) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteelBlue.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SteelBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mengenai Anggaran Kategori",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = SteelBlue
                            )
                            Text(
                                text = "Anggaran Kategori memungkinkan Anda memberikan porsi batasan khusus untuk kategori pengeluaran tertentu (misalnya saja: Makanan, Belanja, Hiburan, dll.). Ini sangat berguna untuk menganalisis dan mencegah keborosan pada sektor pengeluaran tertentu yang spesifik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TranslucentInput, RoundedCornerShape(12.dp))
                        .border(1.dp, GhostWhite.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .clickable { showCategoryPickerPopup = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Pilih Kategori Anggaran",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite
                        )
                        Text(
                            text = if (selectedEnabledCategories.isEmpty()) {
                                "Ketuk untuk memilih kategori..."
                            } else {
                                "${selectedEnabledCategories.size} Kategori terpilih"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.5f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(SteelBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Atur Kategori",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SteelBlue
                        )
                    }
                }

                if (showCategoryPickerPopup) {
                    Dialog(onDismissRequest = { showCategoryPickerPopup = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        GhostWhite.copy(alpha = 0.2f),
                                        GhostWhite.copy(alpha = 0.02f)
                                    )
                                )
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Box(modifier = Modifier.background(TranslucentGlass)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Pilih Kategori Anggaran",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (category in expenseCategories) {
                                                val isChecked = selectedEnabledCategories.contains(category)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (isChecked) SteelBlue.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (isChecked) {
                                                                selectedEnabledCategories.remove(category)
                                                                localCategoryBudgets[category] = ""
                                                            } else {
                                                                selectedEnabledCategories.add(category)
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = category,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isChecked) SteelBlue else GhostWhite.copy(alpha = 0.8f)
                                                    )

                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { checked ->
                                                            if (!checked) {
                                                                selectedEnabledCategories.remove(category)
                                                                localCategoryBudgets[category] = ""
                                                            } else {
                                                                selectedEnabledCategories.add(category)
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = SteelBlue,
                                                            uncheckedColor = GhostWhite.copy(alpha = 0.2f),
                                                            checkmarkColor = MidnightAbyss
                                                        ),
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        PremiumButton(
                                            text = "Selesai",
                                            onClick = { showCategoryPickerPopup = false },
                                            fillMaxWidth = false,
                                            horizontalPadding = 20.dp,
                                            verticalPadding = 6.dp,
                                            modifier = Modifier.height(40.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedEnabledCategories.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (category in selectedEnabledCategories) {
                            val localState = remember(category) { mutableStateOf(localCategoryBudgets[category] ?: "") }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = TranslucentInput
                                ),
                                border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = Color(0xFFF44336),
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GhostWhite
                                        )
                                    }

                                    OutlinedTextField(
                                        value = localState.value,
                                        onValueChange = { newValue: String ->
                                            if (newValue.all { char -> char.isDigit() } || newValue.isEmpty()) {
                                                localState.value = newValue
                                                localCategoryBudgets[category] = newValue
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Limit Rp",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = GhostWhite.copy(alpha = 0.3f)
                                            )
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite, fontSize = 12.sp),
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(46.dp)
                                            .testTag("category_budget_input_${category.lowercase()}"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = GhostWhite,
                                            unfocusedTextColor = GhostWhite,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.1f),
                                            focusedContainerColor = GhostWhite.copy(alpha = 0.03f),
                                            unfocusedContainerColor = GhostWhite.copy(alpha = 0.01f)
                                        )
                                    )

                                    IconButton(
                                        onClick = {
                                            selectedEnabledCategories.remove(category)
                                            localCategoryBudgets[category] = ""
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_category_budget_button_${category.lowercase()}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Batas Kategori $category",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Belum ada kategori dibatasi. Ketuk tombol 'Atur Kategori' di atas untuk membatasi anggaran kategori spesifik.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumButton(
                    text = "Batal",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    isActive = false,
                    testTag = "cancel_budget_button"
                )
                PremiumButton(
                    text = "Simpan",
                    onClick = {
                        val trimmed = budgetInput.trim()
                        if (trimmed.isEmpty()) {
                            budgetError = "Harap masukkan nilai anggaran"
                        } else {
                            val parsed = trimmed.toDoubleOrNull()
                            if (parsed == null || parsed < 0.0) {
                                budgetError = "Nilai harus berupa angka positif"
                            } else {
                                val resultBudgets = mutableMapOf<String, Double>()
                                selectedEnabledCategories.forEach { cat ->
                                    val valStr = localCategoryBudgets[cat] ?: ""
                                    val parsedVal = valStr.toDoubleOrNull()
                                    if (parsedVal != null && parsedVal > 0.0) {
                                        resultBudgets[cat] = parsedVal
                                    }
                                }
                                onConfirm(parsed, resultBudgets)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    isActive = true,
                    testTag = "save_budget_button"
                )
            }
        }
    }
}
}

    if (showDeleteConfirmation) {
        Dialog(onDismissRequest = { showDeleteConfirmation = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Hapus Batas Anggaran?",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Apakah Anda yakin ingin menghapus semua batasan anggaran bulanan serta batasan kategori yang telah diatur?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GhostWhite.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { showDeleteConfirmation = false },
                                modifier = Modifier.weight(1f),
                                isActive = false,
                                testTag = "dismiss_delete_budget_confirm"
                            )
                            PremiumButton(
                                text = "Ya, Hapus",
                                onClick = {
                                    showDeleteConfirmation = false
                                    onConfirm(0.0, emptyMap())
                                },
                                modifier = Modifier.weight(1f),
                                isActive = true,
                                testTag = "confirm_delete_budget_confirm"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionManagementDialog(
    recurringTransactions: List<com.example.data.RecurringTransaction>,
    incomeCategories: List<String>,
    expenseCategories: List<String>,
    onAddRecurring: (description: String, amount: Double, type: String, category: String, dayOfMonth: Int, notes: String) -> Unit,
    onDeleteRecurring: (com.example.data.RecurringTransaction) -> Unit,
    onToggleRecurringActive: (com.example.data.RecurringTransaction) -> Unit,
    onDismissRequest: () -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }

    // Form states
    var descInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var typeInput by remember { mutableStateOf("expense") } // "income" or "expense"
    
    // Choose list based on selected type
    val categories = if (typeInput == "income") incomeCategories else expenseCategories
    var categoryInput by remember(typeInput) { mutableStateOf(categories.firstOrNull() ?: "") }
    
    var dayInput by remember { mutableStateOf(java.time.LocalDate.now().dayOfMonth.toString()) }
    var notesInput by remember { mutableStateOf("") }

    // Errors
    var descError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var dayError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = null,
                    tint = SteelBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isAddingNew) "Tambah Rutinitas" else "Transaksi Berulang",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = GhostWhite
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isAddingNew) {
                // FORM MODE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Atur transaksi otomatis yang akan ditambahkan ke catatan keuangan Anda setiap bulan pada tanggal tertentu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.7f)
                    )

                    // Type Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { typeInput = "expense" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (typeInput == "expense") NeonRed.copy(alpha = 0.15f) else TranslucentGlass
                            ),
                            border = BorderStroke(1.dp, if (typeInput == "expense") NeonRed else GhostWhite.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("Pengeluaran", color = if (typeInput == "expense") NeonRed else GhostWhite.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { typeInput = "income" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (typeInput == "income") NeonGreen.copy(alpha = 0.15f) else TranslucentGlass
                            ),
                            border = BorderStroke(1.dp, if (typeInput == "income") NeonGreen else GhostWhite.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("Pemasukan", color = if (typeInput == "income") NeonGreen else GhostWhite.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Description Input
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = {
                            descInput = it
                            descError = null
                        },
                        label = { Text("Deskripsi/Nama Rutinitas") },
                        isError = descError != null,
                        supportingText = descError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_desc_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput
                        )
                    )

                    // Amount Input
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            amountError = null
                        },
                        label = { Text("Jumlah (Rp)") },
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_amount_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput
                        )
                    )

                    // Category Dropdown/Selector
                    var expandedCat by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedCat,
                        onExpandedChange = { expandedCat = !expandedCat }
                    ) {
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GhostWhite,
                                unfocusedTextColor = GhostWhite,
                                focusedBorderColor = SteelBlue,
                                unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                focusedContainerColor = TranslucentInput,
                                unfocusedContainerColor = TranslucentInput
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCat,
                            onDismissRequest = { expandedCat = false },
                            modifier = Modifier.background(MidnightAbyss)
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category, color = GhostWhite) },
                                    onClick = {
                                        categoryInput = category
                                        expandedCat = false
                                    }
                                )
                            }
                        }
                    }

                    // Day of Month Input
                    OutlinedTextField(
                        value = dayInput,
                        onValueChange = {
                            dayInput = it
                            dayError = null
                        },
                        label = { Text("Setiap Tanggal (1-31)") },
                        placeholder = { Text("Contoh: 6") },
                        isError = dayError != null,
                        supportingText = dayError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_day_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput
                        )
                    )

                    // Notes Input
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Catatan Tambahan (Opsional)") },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_notes_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput
                        )
                    )
                }
            } else {
                // LIST MODE
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (recurringTransactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Belum ada transaksi berulang",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GhostWhite.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Atur tagihan atau pengeluaran bulanan rutin Anda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostWhite.copy(alpha = 0.4f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (item in recurringTransactions) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(if (item.type == "income") NeonGreen else NeonRed, CircleShape)
                                                )
                                                Text(
                                                    text = item.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GhostWhite
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.category} • ${FormatUtils.formatRupiah(item.amount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (item.type == "income") NeonGreen else NeonRed
                                            )
                                            Text(
                                                text = "Tanggal ${item.dayOfMonth} setiap bulan",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GhostWhite.copy(alpha = 0.5f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Switch(
                                                checked = item.isActive,
                                                onCheckedChange = { onToggleRecurringActive(item) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MidnightAbyss,
                                                    checkedTrackColor = SteelBlue,
                                                    uncheckedThumbColor = GhostWhite.copy(alpha = 0.4f),
                                                    uncheckedTrackColor = TranslucentInput
                                                ),
                                                modifier = Modifier.testTag("recurring_toggle_active_${item.id}")
                                            )
                                            IconButton(
                                                onClick = { onDeleteRecurring(item) },
                                                modifier = Modifier.size(36.dp).testTag("delete_recurring_${item.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PremiumButton(
                        text = "Tambah Rutinitas Baru",
                        onClick = { isAddingNew = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_recurring_trigger_button"),
                        isActive = true,
                        icon = androidx.compose.material.icons.Icons.Default.Add
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isAddingNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PremiumButton(
                        text = "Batal",
                        onClick = { isAddingNew = false },
                        modifier = Modifier.weight(1f),
                        isActive = false
                    )
                    PremiumButton(
                        text = "Simpan",
                        onClick = {
                            var valid = true
                            if (descInput.trim().isEmpty()) {
                                descError = "Harap masukkan deskripsi"
                                valid = false
                            }
                            val amt = amountInput.trim().toDoubleOrNull()
                            if (amt == null || amt <= 0.0) {
                                amountError = "Nilai harus berupa angka positif"
                                valid = false
                            }
                            val day = dayInput.trim().toIntOrNull()
                            if (day == null || day < 1 || day > 31) {
                                dayError = "Tanggal harus antara 1-31"
                                valid = false
                            }

                            if (valid && amt != null && day != null) {
                                onAddRecurring(
                                    descInput.trim(),
                                    amt,
                                    typeInput,
                                    categoryInput,
                                    day,
                                    notesInput.trim()
                                )
                                // Clear fields & go back to list
                                descInput = ""
                                amountInput = ""
                                dayInput = java.time.LocalDate.now().dayOfMonth.toString()
                                notesInput = ""
                                isAddingNew = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isActive = true,
                        testTag = "recurring_save_submit"
                    )
                }
            } else {
                PremiumButton(
                    text = "Tutup",
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    isActive = false,
                    fillMaxWidth = true
                )
            }
        }
    }
}
}
}

