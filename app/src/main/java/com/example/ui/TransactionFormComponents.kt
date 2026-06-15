package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.util.CategoryIconMapper

@Composable
fun CategorySelectionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (parsedEmoji, cleanLabel) = CategoryIconMapper.extractEmojiAndLabel(label)
    val icon = CategoryIconMapper.getIcon(label)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (selected) SteelBlue.copy(alpha = 0.5f) else GhostWhite.copy(alpha = 0.2f),
                    if (selected) SteelBlue.copy(alpha = 0.1f) else GhostWhite.copy(alpha = 0.02f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) {
                        Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier.background(Color.Transparent)
                    }
                )
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (parsedEmoji != null) {
                    Text(
                        text = parsedEmoji,
                        fontSize = 16.sp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MidnightAbyss else GhostWhite.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = cleanLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (selected) MidnightAbyss else GhostWhite.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var isCategoryExpanded by remember { mutableStateOf(false) }

    // Premium Acrylic Panel Design
    Box(modifier = Modifier.fillMaxWidth()) {
        // Soft Light Leaks behind panel
        Box(
             modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(SteelBlueGlow.copy(alpha = 0.3f), Color.Transparent),
                        radius = 600f
                    )
                )
                .padding(20.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = TranslucentForm),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.15f),
                        GhostWhite.copy(alpha = 0.02f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Segmented Control (Pemasukan / Pengeluaran) - Stylized like Chat Tab's model switch
                val incomeSelected = type == "income"
                val expenseSelected = type == "expense"
                
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.2f),
                                GhostWhite.copy(alpha = 0.02f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segment: Pemasukan
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (incomeSelected) {
                                        Modifier.background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Modifier.background(Color.Transparent)
                                    }
                                )
                                .clickable { onTypeChanged("income") }
                                .testTag("form_type_income_radio"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pemasukan",
                                color = if (incomeSelected) MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Segment: Pengeluaran
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (expenseSelected) {
                                        Modifier.background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(NeonRed, NeonRed.copy(alpha = 0.7f))
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Modifier.background(Color.Transparent)
                                    }
                                )
                                .clickable { onTypeChanged("expense") }
                                .testTag("form_type_expense_radio"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pengeluaran",
                                color = if (expenseSelected) MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Amount field - Jumbo & Centered
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Jumlah (Rp)", color = MutedExpense, style = MaterialTheme.typography.labelMedium)
                    TextField(
                        value = amount,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                                onAmountChanged(input)
                            }
                        },
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            color = GhostWhite, 
                            textAlign = TextAlign.Center, 
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp
                        ),
                        placeholder = {
                            Text(
                                "0",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = GhostWhite.copy(alpha = 0.2f), 
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Light
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("form_amount_input"),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = GhostWhite
                        )
                    )
                    if (amountError != null) {
                        Text(
                            text = amountError,
                            color = ErrorRed,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Category selection
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCategoryExpanded = !isCategoryExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Kategori",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedExpense
                            )
                            Icon(
                                imageVector = if (isCategoryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCategoryExpanded) "Tutup" else "Buka",
                                tint = MutedExpense,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        TextButton(
                            onClick = onManageCategoriesClick,
                            modifier = Modifier.height(28.dp).testTag("manage_categories_button"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Kelola", color = SteelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    if (isCategoryExpanded) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().testTag("form_category_selection_grid"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                CategorySelectionChip(
                                    selected = category == cat,
                                    label = cat,
                                    onClick = { onCategoryChanged(cat) }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCategoryExpanded = true }
                                .testTag("form_category_selection_grid"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategorySelectionChip(
                                selected = true,
                                label = category,
                                onClick = { isCategoryExpanded = true }
                            )
                            Text(
                                text = "(Ketuk untuk memilih kategori lain)",
                                color = MutedExpense.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Description field
                Column {
                    Text("Deskripsi", color = MutedExpense, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChanged,
                        isError = descriptionError != null,
                        placeholder = { Text("cth: Makan Siang Restoran") },
                        modifier = Modifier.fillMaxWidth().testTag("form_description_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = SteelBlue,
                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                            focusedContainerColor = TranslucentInput,
                            unfocusedContainerColor = TranslucentInput,
                            errorBorderColor = ErrorRed
                        )
                    )
                    if (descriptionError != null) {
                        Text(descriptionError, color = ErrorRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                // Date Picker row triggering Dialog - Redesigned to be a separate premium glass button
                Column {
                    Text("Tanggal", color = MutedExpense, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    GhostWhite.copy(alpha = 0.2f),
                                    GhostWhite.copy(alpha = 0.02f)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("form_date_picker_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDateClick() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (date == 0L) "Pilih Tanggal" else FormatUtils.formatDate(date),
                                color = GhostWhite,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(Icons.Default.CalendarToday, contentDescription = "Kalender", tint = SteelBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (dateError != null) {
                        Text(dateError, color = ErrorRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                // Notes input field
                Column {
                    Text("Catatan (Opsional)", color = MutedExpense, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = onNotesChanged,
                        placeholder = { Text("Ketik catatan di sini...") },
                        modifier = Modifier.fillMaxWidth().testTag("form_notes_input"),
                        shape = RoundedCornerShape(16.dp),
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

                // Actions Button footer - Separated premium glass buttons styled like model switch segments
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reset Button (Inactive segmented style inside glass card)
                    PremiumButton(
                        text = "Reset",
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        isActive = false,
                        testTag = "form_reset_button"
                    )

                    // Simpan Button (Active segmented style inside glass card)
                    PremiumButton(
                        text = if (isEditing) "Simpan Edit" else "Catat Transaksi",
                        onClick = { onSave() },
                        modifier = Modifier.weight(1f),
                        isActive = true,
                        testTag = "form_submit_button"
                    )
                }
            }
        }
    }
}
