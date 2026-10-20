package com.example.ui.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.Eyebrow
import com.example.ui.theme.EditorialTypography
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Spacing

/**
 * Tab Tambah (transaksi baru).
 *
 * Bentuknya mengikuti dua tab yang sudah memakai palet editorial: dari tab
 * Dashboard diambil label mono dan angka serifnya, dari tab Chat diambil pola
 * form-nya (label di atas tiap isian, nominal paling menonjol, jenis transaksi
 * berupa dua pil terbagi rata).
 *
 * Satu halaman datar, tanpa kartu pembungkus. Di layar lebar isinya dibatasi
 * selebar kolom baca supaya baris isian tidak melebar mengikuti layar.
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

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideScreen) Spacing.xl else Spacing.lg)
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
