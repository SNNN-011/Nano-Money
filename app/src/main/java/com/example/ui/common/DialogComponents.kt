package com.example.ui.common

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialDialogCard
import com.example.ui.components.EditorialPill
import com.example.ui.components.EditorialTextField
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.components.LabeledField
import com.example.ui.components.TapField
import com.example.ui.config.FormatUtils
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import java.util.*

@Composable
fun MessageAlertDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit
) {
    // Dialog ini muncul untuk kabar gagal (simpan/hapus/impor), jadi pakai
    // permukaan editorial yang sama dengan dialog lain: krem, garis rambut,
    // judul serif. Sebelumnya kartunya gelap dan hanya muncul saat error,
    // sehingga layar tiba-tiba berubah hitam.
    EditorialDialog(onDismissRequest = onDismissRequest) {
        EditorialDialogCard(title = title) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                lineHeight = 20.sp
            )
            EditorialButton(
                text = "OK",
                onClick = onDismissRequest,
                trailingArrow = false,
                expanded = true,
                testTag = "message_dialog_ok"
            )
        }
    }
}

@Composable
fun DeleteTransactionDialog(
    record: FinancialRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    EditorialDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = BurntOrange,
                    modifier = Modifier.size(40.dp).padding(bottom = Spacing.sm)
                )
                Text(
                    text = "Hapus Transaksi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    fontFamily = SerifFamily,
                    color = Ink,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                Text(
                    text = "Apakah Anda yakin ingin menghapus transaksi \"${record.description}\" senilai ${FormatUtils.formatRupiah(record.amount)}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.xl)
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
                        testTag = "cancel_delete_button"
                    )
                    EditorialButton(
                        text = "Hapus",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        accent = BurntOrange,
                        trailingArrow = false,
                        testTag = "confirm_delete_button"
                    )
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
    onAddCategory: suspend (String) -> Boolean,
    onDeleteCategory: suspend (String) -> Boolean,
    onRenameCategory: suspend (String, String) -> Boolean = { _, _ -> false },
    categoryRecordCounts: Map<String, Int> = emptyMap()
) {
    var newCategoryName by remember { mutableStateOf("") }
    var categoryNameError by remember { mutableStateOf<String?>(null) }

    // H7: hapus kategori butuh konfirmasi; kategori yang menunggu konfirmasi disimpan di sini.
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }

    // Rename inline: ID kategori tidak berubah, hanya nama + updatedAt, sehingga
    // anggaran dan transaksi lama tetap tertaut dan ikut ikut nama baru.
    var renamingCategory by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    val renameScope = rememberCoroutineScope()

    fun startRename(category: String) {
        renamingCategory = category
        renameInput = category
        renameError = null
        categoryNameError = null
    }

    fun cancelRename() {
        renamingCategory = null
        renameInput = ""
        renameError = null
    }

    fun confirmRename(category: String) {
        val trimmed = renameInput.trim()
        when {
            trimmed.isEmpty() -> renameError = "Nama tidak boleh kosong"
            trimmed == category -> cancelRename()
            currentCategories.any { it.equals(trimmed, ignoreCase = true) } ->
                renameError = "Kategori sudah ada"
            else -> renameScope.launch {
                renameError = if (onRenameCategory(category, trimmed)) {
                    cancelRename()
                    null
                } else {
                    "Gagal mengubah nama kategori"
                }
            }
        }
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
                Eyebrow(text = "Kategori")
                Text(
                    text = "Kelola kategori",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
                Text(
                    text = "Daftar kategori ${if (formType == "income") "pendapatan" else "pengeluaran"}. Tambah, ubah nama, atau hapus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )

                // Tambah kategori: isian dan tombol sebaris, sama tinggi. Dulu
                // isiannya kotak Material gelap dengan tombol ikon 52 dp di
                // sebelahnya, jadi kelihatan seperti dua benda beda keluarga.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialTextField(
                        value = newCategoryName,
                        onValueChange = {
                            if (it.length <= 20) {
                                newCategoryName = it
                                categoryNameError = null
                            }
                        },
                        placeholder = "Nama kategori baru",
                        testTag = "new_category_input",
                        modifier = Modifier.weight(1f)
                    )
                    EditorialButton(
                        text = "Tambah",
                        onClick = {
                            val trimmed = newCategoryName.trim()
                            when {
                                trimmed.isEmpty() -> categoryNameError = "Nama tidak boleh kosong"
                                // M13: cek duplikat case-insensitive SEBELUM submit.
                                currentCategories.any { it.equals(trimmed, ignoreCase = true) } ->
                                    categoryNameError = "Kategori sudah ada"
                                else -> renameScope.launch {
                                    val added = onAddCategory(trimmed)
                                    if (added) {
                                        newCategoryName = ""
                                        categoryNameError = null
                                    } else {
                                        categoryNameError = "Gagal menambah kategori (mungkin sudah ada)"
                                    }
                                }
                            }
                        },
                        trailingArrow = false,
                        testTag = "add_category_confirm_button"
                    )
                }
                if (categoryNameError != null) {
                    Text(
                        text = categoryNameError ?: "",
                        color = ErrorRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                HairlineDivider()

                Eyebrow(text = "Daftar kategori")

                // Satu baris per kategori, dipisah garis rambut. Kartu per
                // kategori cuma menambah bingkai di dalam bingkai dialog.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    currentCategories.forEachIndexed { index, cat ->
                        val isRenaming = renamingCategory == cat

                        if (isRenaming) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    EditorialTextField(
                                        value = renameInput,
                                        onValueChange = {
                                            if (it.length <= 30) {
                                                renameInput = it
                                                renameError = null
                                            }
                                        },
                                        placeholder = "Nama baru",
                                        testTag = "rename_category_input",
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { cancelRename() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("cancel_rename_category_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Batal ubah nama",
                                            tint = InkMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { confirmRename(cat) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("confirm_rename_category_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Simpan nama kategori",
                                            tint = Ink,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Anggaran & transaksi lama tetap tertaut",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                                if (renameError != null) {
                                    Text(
                                        text = renameError ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ErrorRed
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Ink,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { startRename(cat) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("rename_category_button_${cat.lowercase(Locale.ROOT)}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah nama kategori $cat",
                                        tint = InkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentCategories.size <= 1) {
                                            categoryNameError = "Tidak bisa menghapus kategori terakhir!"
                                        } else {
                                            // H7: selalu lewat dialog konfirmasi,
                                            // jangan langsung eksekusi.
                                            pendingDeleteCategory = cat
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_category_button_${cat.lowercase(Locale.ROOT)}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Kategori $cat",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (index != currentCategories.lastIndex) HairlineDivider()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EditorialButton(
                        text = "Selesai",
                        onClick = onDismissRequest,
                        trailingArrow = false,
                        testTag = "close_category_dialog_button"
                    )
                }
            }
        }
    }

    // H7: konfirmasi sebelum menghapus; sebutkan berapa transaksi yang ikut dipindah.
    val deletingCategory = pendingDeleteCategory
    if (deletingCategory != null) {
        val movedCount = categoryRecordCounts[deletingCategory] ?: 0
        val fallbackName = FinancialTrackerViewModel.FALLBACK_CATEGORY_NAME
        EditorialDialog(onDismissRequest = { pendingDeleteCategory = null }) {
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
                    Text(
                        text = "Hapus kategori?",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    Text(
                        text = if (movedCount > 0) {
                            "Kategori \"$deletingCategory\" akan dihapus. $movedCount transaksi akan dipindah ke \"$fallbackName\"."
                        } else {
                            "Kategori \"$deletingCategory\" akan dihapus. Transaksi pada kategori ini akan dipindah ke \"$fallbackName\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        EditorialButton(
                            text = "Batal",
                            onClick = { pendingDeleteCategory = null },
                            modifier = Modifier.weight(1f),
                            style = EditorialButtonStyle.Ghost,
                            trailingArrow = false,
                            accent = InkMuted,
                            testTag = "dismiss_delete_category_button"
                        )
                        EditorialButton(
                            text = "Ya, hapus",
                            onClick = {
                                renameScope.launch {
                                    val deleted = onDeleteCategory(deletingCategory)
                                    pendingDeleteCategory = null
                                    categoryNameError = if (deleted) null else "Gagal menghapus kategori!"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            accent = BurntOrange,
                            trailingArrow = false,
                            testTag = "confirm_delete_category_button"
                        )
                    }
                }
            }
        }
    }
}

/**
 * M12: parse nominal anggaran. Hanya digit yang diterima (titik dianggap
 * pemisah ribuan dari formatter), wajib 1..MAX_AMOUNT. Kosong, NaN, negatif,
 * maupun nilai tak terbatas semuanya menghasilkan null.
 */
private fun parseBudgetInput(raw: String): Long? {
    val clean = raw.replace(".", "").trim()
    if (clean.isEmpty() || !clean.all { it.isDigit() }) return null
    val parsed = clean.toLongOrNull() ?: return null
    return if (parsed in 1..com.example.data.FinancialRecordRepository.MAX_AMOUNT) parsed else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetDialog(
    currentBudgetLimit: Long,
    categoryBudgets: Map<String, Long>,
    expenseCategories: List<com.example.data.Category>,
    onConfirm: (Long, Map<String, Long>) -> Unit,
    onDismiss: () -> Unit
) {
    // Kunci state = categoryId (stabil saat kategori di-rename); label = nama terbaru.
    val categoryNameById = remember(expenseCategories) {
        expenseCategories.associate { it.id to it.name }
    }

    var budgetInput by remember {
        val initial = if (currentBudgetLimit > 0L) {
            FormatUtils.formatInputNumber(currentBudgetLimit.toString())
        } else ""
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var budgetError by remember { mutableStateOf<String?>(null) }

    // Map input states for category budgets in a mutable map
    val localCategoryBudgets = remember {
        mutableStateMapOf<String, String>().apply {
            expenseCategories.forEach { category ->
                val value = categoryBudgets[category.id]
                put(category.id, if (value != null && value > 0L) value.toString() else "")
            }
        }
    }

    val selectedEnabledCategories = remember {
        mutableStateListOf<String>().apply {
            expenseCategories.forEach { category ->
                val value = categoryBudgets[category.id]
                if (value != null && value > 0L) {
                    add(category.id)
                }
            }
        }
    }

    var showCategoryPickerPopup by remember { mutableStateOf(false) }
    var showMonthlyBudgetInfo by remember { mutableStateOf(false) }
    var showCategoryBudgetInfo by remember { mutableStateOf(false) }

    EditorialDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.background(CreamRaised)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
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
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showMonthlyBudgetInfo = !showMonthlyBudgetInfo },
                    modifier = Modifier.size(28.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Informasi Anggaran Bulanan",
                        tint = BurntOrange,
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
                        colors = CardDefaults.cardColors(containerColor = Cream),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineStrong),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mengenai Anggaran Bulanan",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = BurntOrange
                            )
                            Text(
                                text = "Batas Anggaran Bulanan menetapkan batas pengeluaran maksimum Anda untuk seluruh pengeluaran dalam satu bulan berjalan. Aplikasi ini membantu mengontrol pengeluaran agar Anda tidak boros dan tetap hemat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Tentukan batas pengeluaran maksimum Anda untuk bulan ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )

                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { field ->
                        // M12: sama seperti field transaksi — hanya digit + pemisah ribuan
                        // yang boleh masuk; huruf/koma ditolak, bukan ditampilkan apa adanya.
                        val clean = field.text.replace(".", "")
                        if (clean.length <= 11 && (clean.isEmpty() || clean.all { it.isDigit() })) {
                            budgetInput = FormatUtils.formatInputNumberPreserveCursor(field)
                        }
                        budgetError = null
                    },
                    label = { Text("Batas Anggaran (Rupiah)", style = MaterialTheme.typography.bodyMedium) },
                    placeholder = { Text("Misal: 5.000.000", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    isError = budgetError != null,
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedLabelColor = Ink,
                        unfocusedLabelColor = InkMuted,
                        focusedPlaceholderColor = InkMuted,
                        unfocusedPlaceholderColor = InkMuted,
                        focusedBorderColor = Ink,
                        unfocusedBorderColor = HairlineStrong,
                        focusedContainerColor = Hairline,
                        unfocusedContainerColor = Cream,
                        cursorColor = Ink,
                        errorBorderColor = BurntOrange,
                        errorTextColor = Ink
                    )
                )

                if (budgetError != null) {
                    Text(
                        text = budgetError ?: "",
                        color = BurntOrange,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // Preset options for better user experience
                Eyebrow(text = "Pilih Cepat")

                val presets = listOf(
                    1_000_000L to "1 Juta",
                    2_000_000L to "2 Juta",
                    5_000_000L to "5 Juta",
                    10_000_000L to "10 Juta"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (value, label) ->
                        val isSelected = budgetInput.text.replace(".", "") == value.toString()
                        val containerCol = if (isSelected) Ink else Cream
                        val borderCol = if (isSelected) Ink else Hairline
                        val textCol = if (isSelected) Cream else InkMuted

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val formatted = FormatUtils.formatInputNumber(value.toLong().toString())
                                    budgetInput = TextFieldValue(formatted, TextRange(formatted.length))
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
                    Eyebrow(
                        text = "Batas Anggaran Per Kategori (Opsional)",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showCategoryBudgetInfo = !showCategoryBudgetInfo },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Informasi Anggaran Kategori",
                            tint = BurntOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showCategoryBudgetInfo) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Cream),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineStrong),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mengenai Anggaran Kategori",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = BurntOrange
                            )
                            Text(
                                text = "Anggaran Kategori memungkinkan Anda memberikan porsi batasan khusus untuk kategori pengeluaran tertentu (misalnya saja: Makanan, Belanja, Hiburan, dll.). Ini sangat berguna untuk menganalisis dan mencegah keborosan pada sektor pengeluaran tertentu yang spesifik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(12.dp))
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
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
                            color = Ink
                        )
                        Text(
                            text = if (selectedEnabledCategories.isEmpty()) {
                                "Ketuk untuk memilih kategori..."
                            } else {
                                "${selectedEnabledCategories.size} Kategori terpilih"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Hairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Atur Kategori",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BurntOrange
                        )
                    }
                }

                if (showCategoryPickerPopup) {
                    EditorialDialog(onDismissRequest = { showCategoryPickerPopup = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CreamRaised),
                            shape = RoundedCornerShape(CornerRadius.bezel),
                            border = BorderStroke(1.dp, Hairline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Box(modifier = Modifier.background(CreamRaised)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.xl),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Pilih Kategori Anggaran",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                        fontFamily = SerifFamily,
                                        color = Ink
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
                                                val categoryId = category.id
                                                val isChecked = selectedEnabledCategories.contains(categoryId)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (isChecked) Cream else Color.Transparent, RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (isChecked) {
                                                                selectedEnabledCategories.remove(categoryId)
                                                                localCategoryBudgets[categoryId] = ""
                                                            } else {
                                                                selectedEnabledCategories.add(categoryId)
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = category.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isChecked) BurntOrange else Ink
                                                    )

                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { checked ->
                                                            if (!checked) {
                                                                selectedEnabledCategories.remove(categoryId)
                                                                localCategoryBudgets[categoryId] = ""
                                                            } else {
                                                                selectedEnabledCategories.add(categoryId)
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = Ink,
                                                            uncheckedColor = HairlineStrong,
                                                            checkmarkColor = Cream
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
                                        EditorialButton(
                                            text = "Selesai",
                                            onClick = { showCategoryPickerPopup = false }
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
                            val localState = remember(category) {
                                val raw = localCategoryBudgets[category] ?: ""
                                val initial = if (raw.isNotEmpty()) FormatUtils.formatInputNumber(raw) else ""
                                mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Cream
                                ),
                                border = BorderStroke(1.dp, Hairline)
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
                                                    color = BurntOrange,
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = categoryNameById[category] ?: category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Ink
                                        )
                                    }

                                    OutlinedTextField(
                                        value = localState.value,
                                        onValueChange = { field ->
                                            // M12: hanya digit; huruf/koma tidak pernah tampil.
                                            val clean = field.text.replace(".", "")
                                            if (clean.length <= 11 && (clean.isEmpty() || clean.all { it.isDigit() })) {
                                                localState.value = FormatUtils.formatInputNumberPreserveCursor(field)
                                                localCategoryBudgets[category] = clean
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Limit Rp",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = InkMuted
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink, fontSize = 12.sp),
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(46.dp)
                                            .testTag("category_budget_input_${category.lowercase()}"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Ink,
                                            unfocusedTextColor = Ink,
                                            focusedBorderColor = Ink,
                                            unfocusedBorderColor = Hairline,
                                            focusedContainerColor = Cream,
                                            unfocusedContainerColor = Cream,
                                            cursorColor = Ink
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
                                            tint = BurntOrange.copy(alpha = 0.8f),
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
                        color = InkMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                    testTag = "cancel_budget_button"
                )
                val parsedBudget = parseBudgetInput(budgetInput.text)
                EditorialButton(
                    text = "Simpan",
                    enabled = parsedBudget != null,
                    // Tombol separuh lebar: panah dimatikan supaya label satu baris.
                    trailingArrow = false,
                    onClick = {
                        if (parsedBudget == null) {
                            budgetError = "Nilai anggaran tidak valid"
                        } else {
                            budgetError = null
                            val resultBudgets = mutableMapOf<String, Long>()
                            selectedEnabledCategories.forEach { cat ->
                                val parsedVal = (localCategoryBudgets[cat] ?: "").toLongOrNull()
                                if (parsedVal != null && parsedVal > 0L) {
                                    resultBudgets[cat] = parsedVal
                                }
                            }
                            onConfirm(parsedBudget, resultBudgets)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "save_budget_button"
                )
            }
        }
    }
}
}

}

/**
 * Konfirmasi hapus batas anggaran bulanan beserta semua batas kategori.
 *
 * Dipanggil dari ikon tong sampah di kartu anggaran tab Beranda. Dulu isinya
 * hidup di dalam [MonthlyBudgetDialog] dengan penanda `showDeleteConfirmation`,
 * tapi tidak ada satu pun tombol yang menyalakannya, jadi dialognya mustahil
 * dibuka dan anggaran bulanan tidak bisa dihapus.
 */
@Composable
fun DeleteBudgetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    EditorialDialog(onDismissRequest = onDismiss) {
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = BurntOrange,
                    modifier = Modifier.size(40.dp).padding(bottom = Spacing.sm)
                )
                Text(
                    text = "Hapus Batas Anggaran?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    fontFamily = SerifFamily,
                    color = Ink,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Apakah Anda yakin ingin menghapus semua batasan anggaran bulanan serta batasan kategori yang telah diatur?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.xl)
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
                        testTag = "dismiss_delete_budget_confirm"
                    )
                    EditorialButton(
                        text = "Ya, Hapus",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        accent = BurntOrange,
                        trailingArrow = false,
                        testTag = "confirm_delete_budget_confirm"
                    )
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
    onAddRecurring: (description: String, amount: Long, type: String, category: String, dayOfMonth: Int, notes: String) -> Unit,
    onDeleteRecurring: (com.example.data.RecurringTransaction) -> Unit,
    onToggleRecurringActive: (com.example.data.RecurringTransaction) -> Unit,
    onDismissRequest: () -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }

    // Form states
    var descInput by remember { mutableStateOf("") }
    // Jumlah disimpan sebagai teks bergaya "1.500.000"; kursor otomatis ke ujung
    // saat menyunting di tengah, cukup untuk kolom angka maksimal 11 digit.
    // ponytail: kalau kursor perlu presisi, pakai formatInputNumberPreserveCursor
    // + TextFieldValue seperti kolom anggaran.
    var amountInput by remember { mutableStateOf("") }
    var typeInput by remember { mutableStateOf("expense") } // "income" or "expense"

    // Choose list based on selected type
    val categories = if (typeInput == "income") incomeCategories else expenseCategories
    var categoryInput by remember(typeInput) { mutableStateOf(categories.firstOrNull() ?: "") }

    var dayInput by remember { mutableStateOf(java.time.LocalDate.now().dayOfMonth.toString()) }
    var notesInput by remember { mutableStateOf("") }

    // Kategori dan tanggal sekarang isian ketuk + daftar pilihan, sama seperti
    // lembar konfirmasi AI di tab Chat.
    var expandedCategoryMenu by remember { mutableStateOf(false) }
    var expandedDayMenu by remember { mutableStateOf(false) }

    EditorialDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.background(CreamRaised)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = null,
                    tint = BurntOrange,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isAddingNew) "Tambah Rutinitas" else "Transaksi Berulang",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
            }
            if (isAddingNew) {
                // FORM MODE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Dulu `weight(1f, fill = false)`: form-nya mengambil
                        // seluruh sisa tinggi jendela dialog, jadi dialognya
                        // melar hampir satu layar walau isinya cuma 5 kolom.
                        // Sekarang tingginya dibatasi dan isinya digulir.
                        // 214 dp = kalimat pengantar + pil jenis + dua isian
                        // penuh, dipilih pas di garis bawah isian "Jumlah".
                        // Batas 200 dp yang lama jatuh di tengah isian, jadi
                        // yang kelihatan separuh kotak kosong tanpa garis bawah.
                        .heightIn(max = 214.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ditambahkan otomatis ke catatan tiap bulan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )

                    // Jenis: dua pil terbagi rata, sama seperti lembar
                    // konfirmasi AI di tab Chat.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        EditorialPill(
                            text = "Pengeluaran",
                            selected = typeInput == "expense",
                            onClick = { typeInput = "expense" },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.labelLarge,
                            contentPadding = Spacing.sm
                        )
                        EditorialPill(
                            text = "Pemasukan",
                            selected = typeInput == "income",
                            onClick = { typeInput = "income" },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.labelLarge,
                            contentPadding = Spacing.sm
                        )
                    }

                    LabeledField(label = "Deskripsi") {
                        EditorialTextField(
                            value = descInput,
                            onValueChange = { if (it.length <= 20) descInput = it },
                            placeholder = "cth: Sewa kos",
                            testTag = "recurring_desc_input"
                        )
                    }

                    LabeledField(label = "Jumlah (Rp)") {
                        EditorialTextField(
                            value = amountInput,
                            onValueChange = { text ->
                                // M12: hanya digit + pemisah ribuan; huruf/koma ditolak sejak awal.
                                val clean = text.replace(".", "")
                                if (clean.length <= 11 && (clean.isEmpty() || clean.all { it.isDigit() })) {
                                    amountInput = FormatUtils.formatInputNumber(clean)
                                }
                            },
                            placeholder = "0",
                            keyboardType = KeyboardType.Number,
                            testTag = "recurring_amount_input"
                        )
                    }

                    LabeledField(label = "Kategori") {
                        Box {
                            TapField(
                                text = categoryInput.ifBlank { "Pilih kategori" },
                                onClick = { expandedCategoryMenu = true },
                                testTag = "recurring_category_input"
                            )
                            DropdownMenu(
                                expanded = expandedCategoryMenu,
                                onDismissRequest = { expandedCategoryMenu = false },
                                modifier = Modifier.background(CreamRaised).border(1.dp, Hairline)
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category, color = Ink) },
                                        onClick = {
                                            categoryInput = category
                                            expandedCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    LabeledField(label = "Setiap Tanggal") {
                        Box {
                            TapField(
                                text = "Tanggal $dayInput",
                                onClick = { expandedDayMenu = true },
                                testTag = "recurring_day_input"
                            )
                            DropdownMenu(
                                expanded = expandedDayMenu,
                                onDismissRequest = { expandedDayMenu = false },
                                modifier = Modifier.background(CreamRaised).border(1.dp, Hairline)
                            ) {
                                (1..31).forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day.toString(), color = Ink) },
                                        onClick = {
                                            dayInput = day.toString()
                                            expandedDayMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    LabeledField(label = "Catatan (opsional)") {
                        EditorialTextField(
                            value = notesInput,
                            onValueChange = { if (it.length <= 100) notesInput = it },
                            placeholder = "Ketik catatan...",
                            testTag = "recurring_notes_input"
                        )
                    }
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
                                    color = InkMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Atur tagihan atau pengeluaran bulanan rutin Anda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
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
                                    colors = CardDefaults.cardColors(containerColor = CreamRaised),
                                    border = BorderStroke(1.dp, Hairline)
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
                                                        .background(if (item.type == "income") ForestGreen else BurntOrange, CircleShape)
                                                )
                                                Text(
                                                    text = item.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Ink
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.category} • ${FormatUtils.formatRupiah(item.amount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (item.type == "income") ForestGreen else BurntOrange
                                            )
                                            Text(
                                                text = "Tanggal ${item.dayOfMonth} setiap bulan",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkMuted
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
                                                    checkedThumbColor = Cream,
                                                    checkedTrackColor = Ink,
                                                    uncheckedThumbColor = InkMuted,
                                                    uncheckedTrackColor = Cream
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
                                                    tint = BurntOrange,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Tidak perlu Spacer lagi: kolom induknya sudah memberi jarak 16 dp
            // antar anak, jadi spacer ini bikin celahnya jadi 32 dp.
            if (isAddingNew) {
                // M12: validasi live; tombol simpan nonaktif sampai semua nilai valid.
                val recurringAmount = amountInput.replace(".", "").trim().toLongOrNull()
                val recurringDay = dayInput.trim().toIntOrNull()
                val recurringFormValid = descInput.trim().isNotEmpty() &&
                    recurringAmount != null &&
                    recurringAmount in 1..com.example.data.FinancialRecordRepository.MAX_AMOUNT &&
                    recurringDay != null && recurringDay in 1..31 &&
                    categoryInput.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { isAddingNew = false },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false
                    )
                    EditorialButton(
                        text = "Simpan",
                        onClick = {
                            if (recurringFormValid && recurringAmount != null && recurringDay != null) {
                                onAddRecurring(
                                    descInput.trim(),
                                    recurringAmount,
                                    typeInput,
                                    categoryInput,
                                    recurringDay,
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
                        enabled = recurringFormValid,
                        trailingArrow = false,
                        testTag = "recurring_save_submit"
                    )
                }
                if (!recurringFormValid) {
                    Text(
                        text = "Lengkapi deskripsi, jumlah, kategori, dan tanggal (1-31) sebelum menyimpan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            } else {
                // Tutup di kiri, aksi utama di kanan: sama dengan pop up hapus
                // (Batal kiri, Hapus kanan). Dua-duanya separuh lebar supaya
                // barisnya simetris, bukan dua pil beda ukuran yang nyempil
                // di kanan.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorialButton(
                        text = "Tutup",
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false
                    )
                    EditorialButton(
                        text = "Tambah",
                        onClick = { isAddingNew = true },
                        modifier = Modifier.weight(1f),
                        trailingArrow = false,
                        testTag = "add_recurring_trigger_button"
                    )
                }
            }
        }
    }
}
}
}

