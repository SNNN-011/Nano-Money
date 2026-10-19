package com.example.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.ui.config.AiModelConfig
import com.example.ui.config.FormatUtils

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
import androidx.compose.ui.window.Dialog
import com.example.ui.util.CategoryIconMapper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.util.FirebaseSyncHelper
import com.example.data.remote.ExtractionResult
import com.example.ui.common.CustomDatePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    // Satu sumber kebenaran: bendera dari ViewModel, bukan hasil hitung ulang isi
    // daftar pesan (dulu keduanya bisa berbeda).
    val isAiProcessing by viewModel.isAiProcessing.collectAsState()
    // Otak yang sedang dipakai: AI Lokal (tanpa internet) atau AI Gemini (online).
    val aiMode by viewModel.aiMode.collectAsState()
    val focusManager = LocalFocusManager.current
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    var isLoggedIn by rememberSaveable { mutableStateOf(FirebaseSyncHelper.isUserSignedIn()) }
    val coroutineScope = rememberCoroutineScope()
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                coroutineScope.launch {
                    val firebaseResult = FirebaseSyncHelper.signInWithGoogleInFirebase(context, account)
                    if (firebaseResult.isSuccess) {
                        isLoggedIn = true
                        Toast.makeText(context, "Berhasil masuk untuk fitur AI!", Toast.LENGTH_SHORT).show()
                        FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
                    } else {
                        Toast.makeText(context, "Gagal terhubung ke Firebase: ${firebaseResult.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: ApiException) {
                val statusCode = e.statusCode
                com.example.util.SecureLog.e("ChatScreen", "Google Sign-In failed: StatusCode=$statusCode", e)
                if (statusCode != 12501) { // 12501 is user canceled
                    Toast.makeText(context, "Gagal masuk: ${e.localizedMessage ?: statusCode}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                com.example.util.SecureLog.e("ChatScreen", "Google Sign-In error", e)
                Toast.makeText(context, "Kesalahan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (!isLoggedIn) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Login Required",
                tint = BlueGradientStart,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Autentikasi Diperlukan",
                style = MaterialTheme.typography.titleLarge,
                color = GhostWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Untuk menggunakan fitur Asisten AI Cerdas, Anda harus masuk dengan Akun Google terlebih dahulu. Hal ini untuk memastikan keamanan permintaan AI Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            PremiumButton(
                text = "Masuk dengan Google",
                onClick = {
                    val client = com.example.util.GoogleDriveHelper.getGoogleSignInClient(context)
                    googleSignInLauncher.launch(client.signInIntent)
                },
                isActive = true,
                icon = Icons.Default.Login,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    var showSourceSelector by rememberSaveable { mutableStateOf(false) }
    var showPendingDatePicker by rememberSaveable { mutableStateOf(false) }
    var showReceiptDatePicker by rememberSaveable { mutableStateOf(false) }
    var showModelInfo by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val maxDimension = 2048
                val scale = minOf(
                    maxDimension / it.width.toFloat(),
                    maxDimension / it.height.toFloat(),
                    1f
                )
                val scaledBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true)
                } else {
                    it
                }
                val softwareBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
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
                    val maxDimension = 2048
                    val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
                    }
                    var sampleSize = 1
                    val w = boundsOptions.outWidth
                    val h = boundsOptions.outHeight
                    if (w > 0 && h > 0) {
                        while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
                            sampleSize *= 2
                        }
                    }
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
                    } ?: throw IllegalStateException("Gagal memuat gambar.")
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val maxDimension = 2048
                        val scale = minOf(
                            maxDimension / info.size.width.toFloat(),
                            maxDimension / info.size.height.toFloat(),
                            1f
                        )
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt()
                        )
                    }
                }
                val maxDimension = 2048
                val scale = minOf(
                    maxDimension / bitmap.width.toFloat(),
                    maxDimension / bitmap.height.toFloat(),
                    1f
                )
                val scaledBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else {
                    bitmap
                }
                val softwareBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
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

    if (showModelInfo) {
        Dialog(onDismissRequest = { showModelInfo = false }) {
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
                            text = "Informasi AI",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Nano AI punya dua otak yang bisa dipilih di atas kolom chat:\n" +
                                "• AI Lokal — jalan tanpa internet dan tanpa kirim data ke mana pun, " +
                                "tapi belum bisa membaca foto struk.\n" +
                                "• AI Gemini — butuh internet, bisa membaca foto struk.\n\n" +
                                "Di AI Gemini, aplikasi memakai satu model utama. Kalau model itu sedang " +
                                "tidak tersedia, aplikasi otomatis mencoba dua model cadangan secara " +
                                "berurutan, jadi tidak perlu memilih model secara manual.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PremiumButton(
                            text = "Mengerti",
                            onClick = { showModelInfo = false },
                            isActive = true,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "model_info_understand_button"
                        )
                    }
                }
            }
        }
    }

    if (showSourceSelector) {
        Dialog(onDismissRequest = { showSourceSelector = false }) {
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
                            text = "Ambil Struk Belanja",
                            style = MaterialTheme.typography.titleLarge,
                            color = GhostWhite,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Silakan pilih sumber foto struk belanja Anda untuk dipindai oleh AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { showSourceSelector = false },
                                isActive = false,
                                testTag = "cancel_source_selector_button",
                                fillMaxWidth = false,
                                horizontalPadding = 24.dp,
                                verticalPadding = 8.dp
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingConfirmationForDialog by viewModel.pendingConfirmation.collectAsState()
    if (showPendingDatePicker) {
        pendingConfirmationForDialog?.let { pending ->
            CustomDatePickerDialog(
                initialTimeMs = pending.date ?: System.currentTimeMillis(),
                onDateSelected = { timestamp ->
                    viewModel.updatePendingTransaction(pending.copy(date = timestamp))
                    showPendingDatePicker = false
                },
                onDismissRequest = { showPendingDatePicker = false }
            )
        }
    }

    val pendingReceiptConfirmationForDialog by viewModel.pendingReceiptConfirmation.collectAsState()
    if (showReceiptDatePicker) {
        pendingReceiptConfirmationForDialog?.let { r ->
            CustomDatePickerDialog(
                initialTimeMs = r.dateMillis,
                onDateSelected = { timestamp ->
                    viewModel.updatePendingReceipt(r.copy(dateMillis = timestamp))
                    showReceiptDatePicker = false
                },
                onDismissRequest = { showReceiptDatePicker = false }
            )
        }
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
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = "Nano AI",
                        color = GhostWhite,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "Aktif & Siap Membantu",
                            color = GhostWhite.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Pilihan model dihapus: chat memakai satu model dengan cadangan
                // otomatis (lihat AiModelConfig.modelsToTry). Tombol info tetap ada
                // supaya user tahu model apa yang sedang dipakai.
                //
                // Ukuran bawaan IconButton TIDAK diperkecil: dulu dipaksa 24.dp
                // sehingga area sentuh ikut mengecil di bawah standar 48dp.
                IconButton(onClick = { showModelInfo = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Model",
                        tint = GhostWhite.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f), thickness = 1.dp)

            // Dua otak, satu kolom chat. AI Lokal jalan tanpa internet tapi belum
            // bisa baca foto struk; AI Gemini sebaliknya.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AiModeChip(
                    label = "AI Lokal",
                    selected = aiMode == AiMode.LOCAL,
                    onClick = { viewModel.setAiMode(AiMode.LOCAL) },
                    testTag = "chat_mode_local_chip"
                )
                AiModeChip(
                    label = "AI Gemini",
                    selected = aiMode == AiMode.GEMINI,
                    onClick = { viewModel.setAiMode(AiMode.GEMINI) },
                    testTag = "chat_mode_gemini_chip"
                )
            }

            // Chat History Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.lg),
                reverseLayout = true
            ) {
                // reverseLayout: item pertama tampil paling bawah. Tombol batal
                // ditaruh paling bawah supaya selalu terlihat saat AI memproses.
                if (isAiProcessing) {
                    item(key = "cancel_ai_request") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = { viewModel.cancelAiProcessing() },
                                modifier = Modifier.testTag("cancel_ai_request_button")
                            ) {
                                Text(
                                    "Batalkan permintaan AI",
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
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
                                record = message.record,
                                isError = message.isError,
                                // Tombol "Coba Lagi" hanya untuk kegagalan yang
                                // teks aslinya masih tersimpan.
                                onRetry = message.retryText?.let { { viewModel.retryMessage(message) } }
                            )
                        }
                        is ChatMessage.TypingIndicator -> {
                            TypingIndicatorRow()
                        }
                        is ChatMessage.ScanningReceiptIndicator -> {
                            ScanningReceiptIndicatorRow()
                        }
                    }
                }
            }

            val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
            val pendingReceiptConfirmation by viewModel.pendingReceiptConfirmation.collectAsState()
            val hasPending = pendingConfirmation != null || pendingReceiptConfirmation != null

            if (pendingConfirmation != null) {
                val pending = pendingConfirmation!!
                var isPendingCardExpanded by rememberSaveable { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .testTag("inline_edit_transaction_card"),
                    shape = RoundedCornerShape(CornerRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Spacing.md)
                            // Isi kartu dibatasi dan digulir sendiri supaya di layar
                            // kecil input bar tidak terdorong keluar layar. Tombol
                            // Ya/Batal ada DI LUAR kartu, jadi tetap terlihat.
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClickLabel = if (isPendingCardExpanded) "Tutup penyesuaian" else "Ubah penyesuaian"
                                ) { isPendingCardExpanded = !isPendingCardExpanded }
                                .padding(vertical = Spacing.xs),
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
                                val amtStr = FormatUtils.formatRupiah(pending.amount ?: 0L)
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
                                val amtStr = if (pending.amount == null || pending.amount == 0L) "" else FormatUtils.formatInputNumber(pending.amount.toString())
                                val amtFieldValue = remember(amtStr) { androidx.compose.ui.text.input.TextFieldValue(amtStr, androidx.compose.ui.text.TextRange(amtStr.length)) }
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
                                        value = amtFieldValue,
                                        onValueChange = { field ->
                                            val clean = field.text.replace(".", "")
                                            if (clean.length <= 11) {
                                                val parsedAmt = clean.toLongOrNull() ?: 0L
                                                viewModel.updatePendingTransaction(pending.copy(amount = parsedAmt))
                                            }
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
                                        if (newValue.length <= 20) viewModel.updatePendingTransaction(pending.copy(description = newValue))
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
                                        if (newValue.length <= 100) viewModel.updatePendingTransaction(pending.copy(notes = newValue))
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
                var isReceiptCardExpanded by rememberSaveable { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .testTag("receipt_edit_card"),
                    shape = RoundedCornerShape(CornerRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
                    border = BorderStroke(1.dp, GhostWhite.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Spacing.md)
                            // Alasan sama dengan kartu transaksi: isi dibatasi dan
                            // digulir sendiri, tombol konfirmasi tetap di luar kartu.
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClickLabel = if (isReceiptCardExpanded) "Tutup penyesuaian" else "Ubah penyesuaian"
                                ) { isReceiptCardExpanded = !isReceiptCardExpanded }
                                .padding(vertical = Spacing.xs),
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
                                        text = "${receipt.grouped.size} kategori transaksi  •  Total: ${FormatUtils.formatRupiah(receipt.total)}",
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
                                            val grpAmtStr = if (grp.amount == 0L) "" else FormatUtils.formatInputNumber(grp.amount.toString())
                                            val grpAmtFieldValue = remember(grpAmtStr) { androidx.compose.ui.text.input.TextFieldValue(grpAmtStr, androidx.compose.ui.text.TextRange(grpAmtStr.length)) }
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
                                                    value = grpAmtFieldValue,
                                                    onValueChange = { field ->
                                                        val clean = field.text.replace(".", "")
                                                        if (clean.length <= 11) {
                                                            val parsedAmt = clean.toLongOrNull() ?: 0L
                                                            val updatedGrouped = receipt.grouped.toMutableList()
                                                            updatedGrouped[index] = grp.copy(amount = parsedAmt)
                                                            val newTotal = updatedGrouped.sumOf { it.amount }
                                                            viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped, total = newTotal))
                                                        }
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
                                                    if (newValue.length <= 20) {
                                                        val updatedGrouped = receipt.grouped.toMutableList()
                                                        updatedGrouped[index] = grp.copy(description = newValue)
                                                        viewModel.updatePendingReceipt(receipt.copy(grouped = updatedGrouped))
                                                    }
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
                        text = "Ya, Simpan",
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
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Foto struk hanya bisa dibaca otak Gemini. Di mode AI Lokal
                // tombolnya diredupkan (tidak disembunyikan) supaya tata letak
                // tidak melompat saat user berganti mode.
                val receiptDisabled = hasPending || isAiProcessing || aiMode == AiMode.LOCAL

                IconButton(
                    onClick = { showSourceSelector = true },
                    enabled = !receiptDisabled,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GhostWhite.copy(alpha = 0.08f))
                        .testTag("chat_receipt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Pindai Struk Belanja",
                        tint = if (receiptDisabled) GhostWhite.copy(alpha = 0.45f) else SteelBlue
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !hasPending && !isAiProcessing,
                    placeholder = {
                        val isScanning = messages.any { it is ChatMessage.ScanningReceiptIndicator }
                        val placeholderText = when {
                            isAiProcessing && isScanning -> "Sedang menganalisis struk..."
                            isAiProcessing -> "AI sedang memproses..."
                            hasPending -> "Selesaikan konfirmasi transaksi..."
                            else -> "Ketik pesan transaksimu..."
                        }
                        Text(
                            placeholderText,
                            color = if (hasPending || isAiProcessing) GhostWhite.copy(alpha = 0.45f) else GhostWhite.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated,
                        disabledContainerColor = SurfaceDisabled,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        disabledTextColor = GhostWhite.copy(alpha = 0.45f),
                        cursorColor = SteelBlue
                    ),
                    shape = RoundedCornerShape(CornerRadius.md),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.trim().isNotEmpty() && !hasPending && !isAiProcessing) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.trim().isNotEmpty() && !hasPending && !isAiProcessing) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.trim().isEmpty() || hasPending || isAiProcessing) {
                                GhostWhite.copy(alpha = 0.08f)
                            } else {
                                SteelBlue
                            }
                        )
                        .testTag("chat_send_button"),
                    enabled = !hasPending && !isAiProcessing && inputText.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputText.trim().isEmpty() || hasPending || isAiProcessing) {
                            GhostWhite.copy(alpha = 0.45f)
                        } else {
                            MidnightAbyss
                        }
                    )
                }
            }
        }
    }
}

/**
 * Pilihan otak chat di atas daftar pesan.
 *
 * Bentuknya sengaja meniru chip pilihan yang sudah dipakai layar lain
 * (MonthSelectChip di tab ekspor): kartu tipis, garis gradasi, dan penanda biru
 * saat terpilih -- supaya dua kontrol sejenis tampil sama di seluruh aplikasi.
 */
@Composable
private fun AiModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerRadius.md))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SteelBlue.copy(alpha = 0.25f) else TranslucentGlass
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = if (selected) {
                    listOf(SteelBlue.copy(alpha = 0.6f), SteelBlue.copy(alpha = 0.2f))
                } else {
                    listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                }
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Dipilih",
                    tint = SteelBlue,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                color = if (selected) GhostWhite else GhostWhite.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}




