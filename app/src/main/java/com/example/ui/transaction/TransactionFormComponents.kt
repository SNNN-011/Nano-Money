package com.example.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialPill
import com.example.ui.components.EditorialTextField
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.components.LabeledField
import com.example.ui.components.TapField
import com.example.ui.config.FormatUtils
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.Cream
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Spacing
import com.example.ui.util.CategoryIconMapper

/**
 * Form transaksi memakai palet editorial, sama seperti tab Dashboard dan tab
 * Chat: label mono kecil di atas tiap isian, nominal serif besar di tengah,
 * dan pemilih jenis berupa dua pil tinta.
 *
 * Tidak ada kartu pembungkus: isian menempel langsung di halaman dan dipisah
 * garis rambut. Kartu di dalam kartu hanya menambah bingkai tanpa menambah
 * informasi.
 */

/** Pil kategori: yang terpilih terisi tinta, sisanya hanya berbingkai rambut. */
@Composable
fun CategorySelectionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (parsedEmoji, cleanLabel) = CategoryIconMapper.extractEmojiAndLabel(label)
    val shape = RoundedCornerShape(CornerRadius.pill)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Ink else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, HairlineStrong, shape)
            )
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (parsedEmoji != null) {
            Text(text = parsedEmoji, fontSize = 13.sp)
        } else {
            Icon(
                imageVector = CategoryIconMapper.getIcon(label),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (selected) Cream else InkMuted
            )
        }
        Text(
            text = cleanLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Cream else InkMuted
        )
    }
}

/** Galat isian: mono kecil, tepat di bawah kolom yang bermasalah. */
@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ErrorRed,
        modifier = Modifier.padding(top = Spacing.xs)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionFormCard(
    description: String,
    amount: String,
    type: String,
    category: String,
    date: Long,
    notes: String,
    descriptionError: String?,
    amountError: String?,
    dateError: String?,
    isEditing: Boolean,
    categories: List<String>,
    onManageCategoriesClick: () -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onDateClick: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Boolean,
    onReset: () -> Unit
) {
    val incomeSelected = type == "income"
    val amountColor = if (incomeSelected) ForestGreen else BurntOrange

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {

        // ── Jenis transaksi: dua pil terbagi rata ──────────────────────
        Eyebrow(text = "Jenis transaksi")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            EditorialPill(
                text = "Pemasukan",
                selected = incomeSelected,
                onClick = { onTypeChanged("income") },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.labelLarge,
                contentPadding = Spacing.sm,
                testTag = "form_type_income_radio"
            )
            EditorialPill(
                text = "Pengeluaran",
                selected = !incomeSelected,
                onClick = { onTypeChanged("expense") },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.labelLarge,
                contentPadding = Spacing.sm,
                testTag = "form_type_expense_radio"
            )
        }

        HairlineDivider()

        // ── Nominal: isian paling menonjol di halaman ini ──────────────
        LabeledField(label = "Jumlah (Rp)") {
            var fieldValue by remember {
                mutableStateOf(TextFieldValue(text = amount, selection = TextRange(amount.length)))
            }
            // Nilai bisa datang dari luar komposisi ini (mode ubah, atau form
            // yang di-reset), jadi isian disamakan lagi saat berubah.
            LaunchedEffect(amount) {
                if (fieldValue.text != amount) {
                    fieldValue = TextFieldValue(
                        text = amount,
                        selection = TextRange(amount.length)
                    )
                }
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { raw ->
                    // Posisi kursor dipertahankan lewat pemformat yang sama
                    // dengan isian nominal di tempat lain, supaya menyisipkan
                    // angka di tengah tidak melempar kursor ke ujung.
                    val next = FormatUtils.formatInputNumberPreserveCursor(raw)
                    if (next.text.count { it.isDigit() } <= 11) {
                        fieldValue = next
                        onAmountChanged(next.text)
                    }
                },
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    color = amountColor,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(amountColor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                // Angka kosong tetap butuh penanda, kalau tidak isiannya
                // kelihatan seperti garis kosong tanpa arti.
                decorationBox = { isian ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    color = InkMuted
                                )
                            )
                        }
                        isian()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs)
                    .testTag("form_amount_input")
            )
            if (amountError != null) FieldError(amountError)
        }

        HairlineDivider()

        // ── Kategori: semua pil terbuka, tidak perlu diketuk dulu ──────
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Eyebrow(text = "Kategori")
                Text(
                    text = "Kelola",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BurntOrange,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CornerRadius.pill))
                        .clickable(onClick = onManageCategoriesClick)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .testTag("manage_categories_button")
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .testTag("form_category_selection_grid"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                categories.forEach { cat ->
                    CategorySelectionChip(
                        selected = category == cat,
                        label = cat,
                        onClick = { onCategoryChanged(cat) }
                    )
                }
            }
        }

        HairlineDivider()

        // ── Detail: deskripsi, tanggal, catatan ────────────────────────
        LabeledField(label = "Deskripsi") {
            EditorialTextField(
                value = description,
                onValueChange = { if (it.length <= 20) onDescriptionChanged(it) },
                placeholder = "cth: Makan siang",
                testTag = "form_description_input"
            )
            if (descriptionError != null) FieldError(descriptionError)
        }

        LabeledField(label = "Tanggal") {
            TapField(
                text = if (date == 0L) "Pilih tanggal" else FormatUtils.formatDate(date),
                onClick = onDateClick,
                testTag = "form_date_picker_button"
            )
            if (dateError != null) FieldError(dateError)
        }

        LabeledField(label = "Catatan (opsional)") {
            EditorialTextField(
                value = notes,
                onValueChange = { if (it.length <= 100) onNotesChanged(it) },
                placeholder = "Ketik catatan...",
                testTag = "form_notes_input"
            )
        }

        // ── Aksi: menempel kanan, sama seperti footer dialog ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialButton(
                text = "Batal",
                onClick = onReset,
                style = EditorialButtonStyle.Ghost,
                trailingArrow = false,
                accent = InkMuted,
                testTag = "form_reset_button"
            )
            EditorialButton(
                text = if (isEditing) "Simpan Edit" else "Catat Transaksi",
                onClick = { onSave() },
                testTag = "form_submit_button"
            )
        }
    }
}
