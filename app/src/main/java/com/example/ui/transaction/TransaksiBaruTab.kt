package com.example.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.BezelCard
import com.example.ui.components.DashedDivider
import com.example.ui.components.Eyebrow
import com.example.ui.components.Stamp
import com.example.ui.config.FormatUtils
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.EditorialTypography
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Spacing
import com.example.ui.util.CategoryIconMapper

/**
 * Tab Tambah (transaksi baru).
 *
 * Bentuknya mengikuti dua tab yang sudah memakai palet editorial: dari tab
 * Dashboard diambil pola "slip" (label mono kecil di atas angka serif besar,
 * isi dipisah garis titik-titik, stempel di kepala kartu), dan dari tab Chat
 * diambil pola form-nya (label di atas tiap isian, nominal paling menonjol,
 * jenis transaksi berupa dua pil terbagi rata).
 *
 * Di layar lebar form ditemani panel ringkasan supaya isian yang masuk bisa
 * diperiksa tanpa harus menggulir balik ke atas.
 */
@Composable
fun TransaksiBaruTabContent(
    isWideScreen: Boolean,
    formDescription: String,
    formAmount: String,
    formType: String,
    formCategory: String,
    formDate: Long,
    formNotes: String,
    descriptionError: String?,
    amountError: String?,
    dateError: String?,
    isEditing: Boolean,
    categoriesState: List<String>,
    onManageCategoriesClick: () -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onDateClick: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveSuccess: () -> Unit,
    onReset: () -> Unit,
    onSaveRecord: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Skala tipe editorial dipasang untuk tab ini, sama seperti tab Dashboard
    // dan tab Chat. Setelah semua tab memakai palet editorial, pembungkus ini
    // bisa dipindah ke MyApplicationTheme.
    MaterialTheme(typography = EditorialTypography) {

        val saveRecord: () -> Boolean = {
            val saved = onSaveRecord()
            if (saved) onSaveSuccess()
            saved
        }

        if (isWideScreen) {
            // TABLET / LAYAR LEBAR: form di kiri, ringkasan di kanan.
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
            ) {
                FormHeader(
                    isEditing = isEditing,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.lg)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Column(modifier = Modifier.weight(6f)) {
                        TransactionFormCard(
                            description = formDescription,
                            amount = formAmount,
                            type = formType,
                            category = formCategory,
                            date = formDate,
                            notes = formNotes,
                            descriptionError = descriptionError,
                            amountError = amountError,
                            dateError = dateError,
                            isEditing = isEditing,
                            categories = categoriesState,
                            onManageCategoriesClick = { onManageCategoriesClick() },
                            onDescriptionChanged = onDescriptionChanged,
                            onAmountChanged = onAmountChanged,
                            onTypeChanged = onTypeChanged,
                            onCategoryChanged = onCategoryChanged,
                            onDateClick = onDateClick,
                            onNotesChanged = onNotesChanged,
                            onSave = saveRecord,
                            onReset = onReset
                        )
                    }
                    Column(
                        modifier = Modifier.weight(4f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        TransactionSummaryCard(
                            type = formType,
                            amount = formAmount,
                            category = formCategory,
                            date = formDate,
                            description = formDescription,
                            notes = formNotes
                        )
                        RecordingGuideCard()
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        } else {
            // PONSEL: satu permukaan gulir, header lalu form.
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
            ) {
                FormHeader(
                    isEditing = isEditing,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.lg)
                )
                TransactionFormCard(
                    description = formDescription,
                    amount = formAmount,
                    type = formType,
                    category = formCategory,
                    date = formDate,
                    notes = formNotes,
                    descriptionError = descriptionError,
                    amountError = amountError,
                    dateError = dateError,
                    isEditing = isEditing,
                    categories = categoriesState,
                    onManageCategoriesClick = { onManageCategoriesClick() },
                    onDescriptionChanged = onDescriptionChanged,
                    onAmountChanged = onAmountChanged,
                    onTypeChanged = onTypeChanged,
                    onCategoryChanged = onCategoryChanged,
                    onDateClick = onDateClick,
                    onNotesChanged = onNotesChanged,
                    onSave = saveRecord,
                    onReset = onReset
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}

/** Kepala halaman: label mono, judul serif, satu kalimat penjelas. */
@Composable
private fun FormHeader(
    isEditing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Eyebrow(text = if (isEditing) "Ubah catatan" else "Transaksi baru")
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = if (isEditing) "Ubah transaksi" else "Tambah transaksi",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "Isi nominal dulu, sisanya menyusul. Semua isian masih bisa diubah sebelum disimpan.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )
    }
}

/**
 * Pratinjau isian dalam bentuk slip, memakai pola kepala kartu Dashboard:
 * label mono + stempel di kanan, nominal serif besar, lalu daftar fakta yang
 * dipisah garis titik-titik.
 */
@Composable
private fun TransactionSummaryCard(
    type: String,
    amount: String,
    category: String,
    date: Long,
    description: String,
    notes: String,
    modifier: Modifier = Modifier
) {
    val isIncome = type == "income"
    val accent = if (isIncome) ForestGreen else BurntOrange
    val sign = if (isIncome) "+" else "-"
    val amountText = "$sign Rp ${amount.ifEmpty { "0" }}"
    val categoryLabel = CategoryIconMapper.extractEmojiAndLabel(category).second

    BezelCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Eyebrow(text = "Ringkasan")
                Stamp(text = if (isIncome) "MASUK" else "KELUAR", color = accent)
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.displaySmall,
                color = if (amount.isEmpty()) InkMuted else accent,
                maxLines = 1
            )
            DashedDivider()
            SummaryRow(
                label = "Kategori",
                value = categoryLabel.ifBlank { "Belum dipilih" }
            )
            SummaryRow(
                label = "Tanggal",
                value = if (date == 0L) "Belum dipilih" else FormatUtils.formatDate(date)
            )
            SummaryRow(
                label = "Deskripsi",
                value = description.ifBlank { "Belum diisi" }
            )
            SummaryRow(
                label = "Catatan",
                value = notes.ifBlank { "Belum diisi" }
            )
        }
    }
}

/** Satu baris fakta slip: label mono di kiri, nilai di kanan. */
@Composable
private fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Eyebrow(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = Spacing.md)
                .weight(1f)
        )
    }
}

/** Panduan singkat; nomor langkah memakai stempel mono seperti di Dashboard. */
@Composable
private fun RecordingGuideCard(modifier: Modifier = Modifier) {
    val steps = listOf(
        "Pilih jenis transaksi: pemasukan atau pengeluaran.",
        "Isi nominalnya; angka besar di tengah form itu isian utamanya.",
        "Ketuk satu kategori supaya laporan bulanan tetap rapi.",
        "Ubah tanggal kalau yang dicatat transaksi beberapa hari lalu.",
        "Tekan Catat Transaksi, lalu catatannya muncul di Dashboard."
    )

    BezelCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Eyebrow(text = "Panduan mencatat")
            Text(
                text = "Lima langkah, selesai kurang dari setengah menit.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Stamp(text = "0${index + 1}")
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }
            }
        }
    }
}
