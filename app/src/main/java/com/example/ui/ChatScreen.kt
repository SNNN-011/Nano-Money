package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.ui.util.CategoryIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val focusManager = LocalFocusManager.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    var showSourceSelector by remember { mutableStateOf(false) }
    var showPendingDatePicker by remember { mutableStateOf(false) }
    var showReceiptDatePicker by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val softwareBitmap = it.copy(Bitmap.Config.ARGB_8888, false)
                viewModel.scanReceipt(softwareBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                viewModel.scanReceipt(softwareBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto scroll to bottom when new messages arrive (with reverseLayout, bottom is index 0)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (showSourceSelector) {
        AlertDialog(
            onDismissRequest = { showSourceSelector = false },
            title = {
                Text(
                    text = "Ambil Struk Belanja",
                    style = MaterialTheme.typography.titleMedium,
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Silakan pilih sumber foto struk belanja Anda untuk dipindai oleh AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    PremiumButton(
                        text = "Ambil Foto Langsung",
                        onClick = {
                            showSourceSelector = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isActive = true,
                        icon = Icons.Default.AddAPhoto,
                        testTag = "camera_button"
                    )

                    PremiumButton(
                        text = "Pilih dari Galeri",
                        onClick = {
                            showSourceSelector = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isActive = false,
                        icon = Icons.Default.Image,
                        testTag = "gallery_button"
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                PremiumButton(
                    text = "Batal",
                    onClick = { showSourceSelector = false },
                    isActive = false,
                    testTag = "cancel_source_selector_button",
                    fillMaxWidth = false
                )
            },
            containerColor = MidnightAbyss,
            shape = RoundedCornerShape(24.dp)
        )
    }

    val pendingConfirmationForDialog by viewModel.pendingConfirmation.collectAsState()
    if (showPendingDatePicker && pendingConfirmationForDialog != null) {
        val pending = pendingConfirmationForDialog!!
        CustomDatePickerDialog(
            initialTimeMs = pending.date ?: System.currentTimeMillis(),
            onDateSelected = { timestamp ->
                viewModel.updatePendingTransaction(pending.copy(date = timestamp))
                showPendingDatePicker = false
            },
            onDismissRequest = { showPendingDatePicker = false }
        )
    }

    val pendingReceiptConfirmationForDialog by viewModel.pendingReceiptConfirmation.collectAsState()
    if (showReceiptDatePicker && pendingReceiptConfirmationForDialog != null) {
        val r = pendingReceiptConfirmationForDialog!!
        CustomDatePickerDialog(
            initialTimeMs = r.dateMillis,
            onDateSelected = { timestamp ->
                viewModel.updatePendingReceipt(r.copy(dateMillis = timestamp))
                showReceiptDatePicker = false
            },
            onDismissRequest = { showReceiptDatePicker = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TranslucentForm)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SteelBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Bot AI",
                        tint = MidnightAbyss,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Nano AI",
                        fontWeight = FontWeight.Bold,
                        color = GhostWhite,
                        fontSize = 15.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "Aktif & Siap Membantu",
                            color = GhostWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                
                val selectedModel by viewModel.selectedModel.collectAsState()
                val isGemma = selectedModel == AiModelConfig.GEMMA_MODEL
                val isGemini = selectedModel == AiModelConfig.GEMINI_MODEL

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
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segment: Gemma
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isGemma) SteelBlue else Color.Transparent)
                                .clickable { viewModel.updateSelectedModel(AiModelConfig.GEMMA_MODEL) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("model_switch_gemma"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Gemma",
                                color = if (isGemma) MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Segment: Gemini
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isGemini) SteelBlue else Color.Transparent)
                                .clickable { viewModel.updateSelectedModel(AiModelConfig.GEMINI_MODEL) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("model_switch_gemini"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Gemini",
                                color = if (isGemini) MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f), thickness = 1.dp)

            // Chat History Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                reverseLayout = true
            ) {
                items(messages.asReversed()) { message ->
                    when (message) {
                        is ChatMessage.UserMessage -> {
                            UserMessageBubble(text = message.text)
                        }
                        is ChatMessage.UserImageMessage -> {
                            UserImageMessageBubble(bitmap = message.bitmap)
                        }
                        is ChatMessage.AiMessage -> {
                            AiMessageBubble(
                                text = message.text,
                                record = message.record
                            )
                        }
                        is ChatMessage.TypingIndicator -> {
                            TypingIndicatorRow()
                        }
                    }
                }
            }

            val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
            val pendingReceiptConfirmation by viewModel.pendingReceiptConfirmation.collectAsState()
            val hasPending = pendingConfirmation != null || pendingReceiptConfirmation != null

            if (pendingConfirmation != null) {
                val pending = pendingConfirmation!!
                var isPendingCardExpanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("inline_edit_transaction_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPendingCardExpanded = !isPendingCardExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SESUAIKAN TRANSAKSI AI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = SteelBlue
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPendingCardExpanded) "Tutup" else "Ubah",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SteelBlue,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Icon(
                                    imageVector = if (isPendingCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = SteelBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        if (!isPendingCardExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GhostWhite.copy(alpha = 0.03f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isIncome = pending.type?.lowercase() == "income" || pending.type?.lowercase() == "pemasukan"
                                val typeIcon = if (isIncome) "🟢 Masuk" else "🔴 Keluar"
                                val amtStr = FormatUtils.formatRupiah(pending.amount ?: 0.0)
                                val desc = if (pending.description.isNullOrBlank()) "Tanpa deskripsi" else pending.description
                                val category = pending.category ?: "Umum"

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$typeIcon  $amtStr",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$desc (${category})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GhostWhite.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Type tag toggle
                                val isIncome = pending.type?.lowercase() == "income" || pending.type?.lowercase() == "pemasukan"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GhostWhite.copy(alpha = 0.05f))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val newType = if (isIncome) "expense" else "income"
                                            viewModel.updatePendingTransaction(pending.copy(type = newType))
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isIncome) "🟢 Masuk" else "🔴 Keluar",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite
                                    )
                                }

                                // Category selector tag
                                val incomeCategories by viewModel.incomeCategories.collectAsState()
                                val expenseCategories by viewModel.expenseCategories.collectAsState()
                                val cats = if (isIncome) incomeCategories else expenseCategories
                                var categoryDropdownExpanded by remember { mutableStateOf(false) }

                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GhostWhite.copy(alpha = 0.05f))
                                            .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .clickable { categoryDropdownExpanded = true }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val currentCat = pending.category ?: "Kategori"
                                        Text(
                                            text = currentCat,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = SteelBlue,
                                            maxLines = 1
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                    }

                                    DropdownMenu(
                                        expanded = categoryDropdownExpanded,
                                        onDismissRequest = { categoryDropdownExpanded = false },
                                        modifier = Modifier.background(MidnightAbyss).border(1.dp, GhostWhite.copy(alpha = 0.1f))
                                    ) {
                                        cats.forEach { categoryLabel ->
                                            val (_, label) = CategoryIconMapper.extractEmojiAndLabel(categoryLabel)
                                            DropdownMenuItem(
                                                text = { Text(categoryLabel, color = GhostWhite) },
                                                onClick = {
                                                    viewModel.updatePendingTransaction(pending.copy(category = label))
                                                    categoryDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Amount block (compact basic input field)
                                val amtStr = if (pending.amount == null || pending.amount == 0.0) "" else pending.amount.toInt().toString()
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GhostWhite.copy(alpha = 0.05f))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Rp", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = GhostWhite.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = amtStr,
                                        onValueChange = { newValue ->
                                            val parsedAmt = newValue.toDoubleOrNull() ?: 0.0
                                            viewModel.updatePendingTransaction(pending.copy(amount = parsedAmt))
                                        },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite, fontWeight = FontWeight.Bold),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Row 2: Description
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GhostWhite.copy(alpha = 0.03f))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = pending.description ?: "",
                                    onValueChange = { newValue ->
                                        viewModel.updatePendingTransaction(pending.copy(description = newValue))
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Row 3: Notes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GhostWhite.copy(alpha = 0.02f))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = pending.notes ?: "",
                                    onValueChange = { newValue ->
                                        viewModel.updatePendingTransaction(pending.copy(notes = newValue))
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite.copy(alpha = 0.6f)),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Row 4: Date selection
                            val curDate = pending.date ?: System.currentTimeMillis()
                            val formattedDate = java.text.SimpleDateFormat(
                                "dd MMM yyyy", 
                                java.util.Locale.forLanguageTag("id")
                            ).format(java.util.Date(curDate))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GhostWhite.copy(alpha = 0.03f))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .clickable { showPendingDatePicker = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tanggal: $formattedDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TranslucentForm)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PremiumButton(
                        text = "Ya, Simpan",
                        onClick = { viewModel.confirmPendingTransaction() },
                        modifier = Modifier.weight(1f),
                        isActive = true,
                        icon = Icons.Default.Check,
                        testTag = "confirm_yes_button"
                    )

                    PremiumButton(
                        text = "Batal",
                        onClick = { viewModel.cancelPendingTransaction() },
                        modifier = Modifier.weight(1f),
                        isActive = false,
                        icon = Icons.Default.Close,
                        testTag = "confirm_no_button"
                    )
                }
            }

            if (pendingReceiptConfirmation != null) {
                val receipt = pendingReceiptConfirmation!!
                var expandedReceiptIndices by remember { mutableStateOf(setOf<Int>()) }
                var isReceiptCardExpanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("receipt_edit_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isReceiptCardExpanded = !isReceiptCardExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SESUAIKAN STRUK BELANJA AI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = SteelBlue
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isReceiptCardExpanded) "Tutup" else "Ubah",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SteelBlue,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Icon(
                                    imageVector = if (isReceiptCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = SteelBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (!isReceiptCardExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GhostWhite.copy(alpha = 0.03f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = receipt.title.ifBlank { "Struk Belanja AI" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${receipt.grouped.size} kelompok transaksi  •  Total: ${FormatUtils.formatRupiah(receipt.total)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GhostWhite.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {

                        // 1. Merchant Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GhostWhite.copy(alpha = 0.04f))
                                .border(1.dp, GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = receipt.title,
                                onValueChange = { newValue ->
                                    viewModel.updatePendingReceipt(receipt.copy(title = newValue))
                                },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite, fontWeight = FontWeight.Bold),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 2. Date row
                        val formattedReceiptDate = java.text.SimpleDateFormat(
                            "dd MMM yyyy",
                            java.util.Locale.forLanguageTag("id")
                        ).format(java.util.Date(receipt.dateMillis))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GhostWhite.copy(alpha = 0.03f))
                                .border(1.dp, GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .clickable { showReceiptDatePicker = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GhostWhite.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tanggal: $formattedReceiptDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        }

                        // 3. Collapsible Categories List
                        Text(
                            text = "Rencana Catatan Transaksi (Dapat Dibuka-Tutup):",
                            style = MaterialTheme.typography.labelSmall.copy(color = GhostWhite.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        receipt.grouped.forEachIndexed { index, grp ->
                            val isExpanded = expandedReceiptIndices.contains(index)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = GhostWhite.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    // Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedReceiptIndices = if (isExpanded) {
                                                    expandedReceiptIndices - index
                                                } else {
                                                    expandedReceiptIndices + index
                                                }
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(grp.emoji, fontSize = 14.sp)
                                            Text(
                                                text = grp.category,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = SteelBlue,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = grp.description.ifBlank { "" },
                                                style = MaterialTheme.typography.bodySmall.copy(color = GhostWhite.copy(alpha = 0.5f)),
                                                maxLines = 1,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = FormatUtils.formatRupiah(grp.amount),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = GhostWhite
                                            )
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Sembunyikan" else "Tampilkan",
                                                tint = GhostWhite.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Content settings if expanded
                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isItemIncome = grp.type == "income"
                                            
                                            // Item Type toggle
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(GhostWhite.copy(alpha = 0.05f))
                                                    .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        val newType = if (isItemIncome) "expense" else "income"
                                                        val updatedGrouped = receipt.grouped.toMutableList()
                                                        updatedGrouped[index] = grp.copy(type = newType)
                                                        viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped))
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isItemIncome) "🟢 Masuk" else "🔴 Keluar",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                    color = GhostWhite
                                                )
                                            }

                                            // Item Category dropdown
                                            val incomeCategories by viewModel.incomeCategories.collectAsState()
                                            val expenseCategories by viewModel.expenseCategories.collectAsState()
                                            val cats = if (isItemIncome) incomeCategories else expenseCategories
                                            var itemDropdownExpanded by remember { mutableStateOf(false) }

                                            Box {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(GhostWhite.copy(alpha = 0.05f))
                                                        .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                        .clickable { itemDropdownExpanded = true }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = grp.category,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                        color = SteelBlue,
                                                        maxLines = 1
                                                    )
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                                }

                                                DropdownMenu(
                                                    expanded = itemDropdownExpanded,
                                                    onDismissRequest = { itemDropdownExpanded = false },
                                                    modifier = Modifier.background(MidnightAbyss).border(1.dp, GhostWhite.copy(alpha = 0.1f))
                                                ) {
                                                    cats.forEach { categoryLabel ->
                                                        val (_, label) = CategoryIconMapper.extractEmojiAndLabel(categoryLabel)
                                                        DropdownMenuItem(
                                                            text = { Text(categoryLabel, color = GhostWhite, fontSize = 12.sp) },
                                                            onClick = {
                                                                val updatedGrouped = receipt.grouped.toMutableList()
                                                                updatedGrouped[index] = grp.copy(category = label)
                                                                viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped))
                                                                itemDropdownExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Item Amount basic numeric field
                                            val grpAmtStr = if (grp.amount == 0.0) "" else grp.amount.toInt().toString()
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(GhostWhite.copy(alpha = 0.05f))
                                                    .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Rp", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = GhostWhite.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = grpAmtStr,
                                                    onValueChange = { newValue ->
                                                        val parsedAmt = newValue.toDoubleOrNull() ?: 0.0
                                                        val updatedGrouped = receipt.grouped.toMutableList()
                                                        updatedGrouped[index] = grp.copy(amount = parsedAmt)
                                                        val newTotal = updatedGrouped.sumOf { it.amount }
                                                        viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped, total = newTotal))
                                                    },
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Item Description basic field
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(GhostWhite.copy(alpha = 0.02f))
                                                .border(1.dp, GhostWhite.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = grp.description,
                                                onValueChange = { newValue ->
                                                    val updatedGrouped = receipt.grouped.toMutableList()
                                                    updatedGrouped[index] = grp.copy(description = newValue)
                                                    viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped))
                                                },
                                                textStyle = MaterialTheme.typography.bodySmall.copy(color = GhostWhite, fontSize = 11.sp),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(GhostWhite),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TranslucentForm)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PremiumButton(
                        text = "Ya, Simpan Kelompok",
                        onClick = { viewModel.confirmPendingReceipt() },
                        modifier = Modifier.weight(1f),
                        isActive = true,
                        icon = Icons.Default.Check,
                        testTag = "confirm_receipt_yes_button"
                    )

                    PremiumButton(
                        text = "Batal",
                        onClick = { viewModel.cancelPendingReceipt() },
                        modifier = Modifier.weight(1f),
                        isActive = false,
                        icon = Icons.Default.Close,
                        testTag = "confirm_receipt_no_button"
                    )
                }
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f), thickness = 1.dp)

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TranslucentForm)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { showSourceSelector = true },
                    enabled = !hasPending,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(GhostWhite.copy(alpha = 0.08f))
                        .testTag("chat_receipt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Pindai Struk Belanja",
                        tint = if (hasPending) GhostWhite.copy(alpha = 0.2f) else SteelBlue
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !hasPending,
                    placeholder = {
                        Text(
                            if (hasPending) "Selesaikan konfirmasi transaksi..." else "Ketik pesan transaksimu...",
                            color = if (hasPending) GhostWhite.copy(alpha = 0.2f) else GhostWhite.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        disabledContainerColor = Color(0xFF0F0F14),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        disabledTextColor = GhostWhite.copy(alpha = 0.2f),
                        cursorColor = SteelBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.trim().isNotEmpty() && !hasPending) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.trim().isNotEmpty() && !hasPending) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.trim().isEmpty() || hasPending) {
                                GhostWhite.copy(alpha = 0.08f)
                            } else {
                                SteelBlue
                            }
                        )
                        .testTag("chat_send_button"),
                    enabled = !hasPending && inputText.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputText.trim().isEmpty() || hasPending) {
                            GhostWhite.copy(alpha = 0.3f)
                        } else {
                            MidnightAbyss
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(text: String) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_user"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.25f),
                                GhostWhite.copy(alpha = 0.03f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    color = GhostWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun UserImageMessageBubble(bitmap: Bitmap) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_user_image"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.25f),
                                GhostWhite.copy(alpha = 0.03f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(6.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Struk Belanjaan",
                    modifier = Modifier
                        .sizeIn(maxWidth = 160.dp, maxHeight = 160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun AiMessageBubble(text: String, record: FinancialRecord?) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_ai"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(TranslucentGlass)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.2f),
                                GhostWhite.copy(alpha = 0.02f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    color = GhostWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // Confirmation transaction card
            if (record != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmationTransactionCard(record = record)
            }
        }
    }
}

@Composable
fun ConfirmationTransactionCard(record: FinancialRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .testTag("confirmation_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, TranslucentBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Pill: Room Saved Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Tersimpan",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Description and Amount
            Column {
                Text(
                    text = record.description,
                    fontWeight = FontWeight.Bold,
                    color = GhostWhite,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                val isIncome = record.type == "income"
                Text(
                    text = "${if (isIncome) "+" else "-"} ${FormatUtils.formatRupiah(record.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isIncome) NeonGreen else NeonRed,
                    fontSize = 18.sp
                )
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f))

            // Metadata Detail Grid (Kategori, Tanggal, Notes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category
                val (parsedEmoji, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(record.category)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (parsedEmoji != null) {
                        Text(
                            text = parsedEmoji,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = CategoryIconMapper.getIcon(record.category),
                            contentDescription = "Kategori",
                            tint = SteelBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = cleanCategory,
                        color = GhostWhite.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Tanggal",
                        tint = SteelBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = FormatUtils.formatDate(record.date),
                        color = GhostWhite.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("typing_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                TypingIndicatorBubble()
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0
                1.0f at 200
                0.2f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 150
                1.0f at 350
                0.2f at 550
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 300
                1.0f at 500
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot3Alpha)))
    }
}
