package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

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
    if (isWideScreen) {
        // TABLET / WIDE SCREENS LAYOUT
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: The entire comprehensive input form Panel
            Box(modifier = Modifier.weight(6f).fillMaxHeight()) {
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
                    onSave = {
                        val saved = onSaveRecord()
                        if (saved) {
                            onSaveSuccess()
                        }
                        saved
                    },
                    onReset = onReset
                )
            }

            // Right Column: A small elegant helper guideline of modern transaction entry
            Card(
                modifier = Modifier.weight(4f).fillMaxHeight(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SteelBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Panduan Mencatat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. Pilih jenis transaksi (Pemasukan / Pengeluaran)\n2. Masukkan nominal uang\n3. Berikan kategori yang sesuai\n4. Tulis deskripsi pelengkap seperti nama toko atau keperluan transaksi\n5. Ketuk 'Catat Transaksi' untuk menyimpan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    } else {
        // MOBILE COMFORT LAYOUT
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
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
                    onSave = {
                        val saved = onSaveRecord()
                        if (saved) {
                            onSaveSuccess()
                        }
                        saved
                    },
                    onReset = onReset
                )
        }
    }
}
