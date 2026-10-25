package com.example.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.FinancialRecord
import com.example.domain.ParsedReceipt
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
import com.example.ui.util.CategoryIconMapper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.util.FirebaseSyncHelper
import com.example.data.remote.ExtractionResult
import com.example.ui.common.CustomDatePickerDialog
import com.example.ui.components.DashedDivider
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialPill
import com.example.ui.components.EditorialTextField
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.components.LabeledField
import com.example.ui.components.TapField
import com.example.ui.components.editorialFieldColors

/**
 * Tab Chat memakai palet editorial seperti tab Dashboard.
 *
 * Pembungkus `MaterialTheme` ada di sini (bukan menempel di setiap elemen)
 * supaya seluruh isi tab, termasuk pop up yang dikomposisi di jendela
 * terpisah, memakai skala tipe yang sama.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    MaterialTheme(typography = EditorialTypography) {
        ChatScreenContent(viewModel = viewModel, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    // Satu sumber kebenaran: bendera dari ViewModel, bukan hasil hitung ulang isi
    // daftar pesan (dulu keduanya bisa berbeda).
    val isAiProcessing by viewModel.isAiProcessing.collectAsState()
    // Otak yang sedang dipakai: AI Lokal (tanpa internet) atau AI Cloud (online).
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
                tint = BurntOrange,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Autentikasi Diperlukan",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Untuk menggunakan fitur Asisten AI Cerdas, Anda harus masuk dengan Akun Google terlebih dahulu. Hal ini untuk memastikan keamanan permintaan AI Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            EditorialButton(
                text = "Masuk dengan Google",
                onClick = {
                    val client = com.example.util.GoogleDriveHelper.getGoogleSignInClient(context)
                    googleSignInLauncher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                expanded = true
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
        EditorialDialog(onDismissRequest = { showModelInfo = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.bezel))
                    .background(CreamRaised)
                    .border(1.dp, Hairline, RoundedCornerShape(CornerRadius.bezel))
                    .padding(Spacing.xl)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Informasi AI",
                        color = Ink,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = "Nano AI punya dua otak yang bisa dipilih di kanan judul chat:\n" +
                            "â€¢ AI Lokal â€” jalan tanpa internet dan tanpa kirim data ke mana pun, " +
                            "tapi belum bisa membaca foto struk.\n" +
                            "â€¢ AI Cloud â€” butuh internet, bisa membaca foto struk.\n\n" +
                            "Di AI Cloud, aplikasi memakai satu model utama. Kalau model itu sedang " +
                            "tidak tersedia, aplikasi otomatis mencoba dua model cadangan secara " +
                            "berurutan, jadi tidak perlu memilih model secara manual.",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    EditorialButton(
                        text = "Mengerti",
                        onClick = { showModelInfo = false },
                        // Tombol penutup: selebar labelnya dan rata kanan, sama
                        // seperti dialog penjelasan lain di aplikasi.
                        modifier = Modifier.align(Alignment.End),
                        testTag = "model_info_understand_button"
                    )
                }
            }
        }
    }

    if (showSourceSelector) {
        EditorialDialog(onDismissRequest = { showSourceSelector = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.bezel))
                    .background(CreamRaised)
                    .border(1.dp, Hairline, RoundedCornerShape(CornerRadius.bezel))
                    .padding(Spacing.xl)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Ambil Struk Belanja",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink
                    )

                    Text(
                        text = "Silakan pilih sumber foto struk belanja Anda untuk dipindai oleh AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        EditorialButton(
                            text = "Ambil Foto Langsung",
                            onClick = {
                                showSourceSelector = false
                                cameraLauncher.launch(null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            expanded = true,
                            testTag = "camera_button"
                        )

                        EditorialButton(
                            text = "Pilih dari Galeri",
                            onClick = {
                                showSourceSelector = false
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = EditorialButtonStyle.Ghost,
                            trailingArrow = false,
                            testTag = "gallery_button"
                        )
                    }

                    EditorialButton(
                        text = "Batal",
                        onClick = { showSourceSelector = false },
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        modifier = Modifier.align(Alignment.End),
                        testTag = "cancel_source_selector_button"
                    )
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
                    .background(CreamRaised)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Ink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Bot AI",
                        tint = Cream,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    // Judul, tombol info, dan pemilih otak berbagi satu baris:
                    // pil otak duduk tepat di kanan tombol (i), sejajar judul.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "Nano AI",
                            color = Ink,
                            style = MaterialTheme.typography.titleMedium
                        )

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
                                tint = InkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Dua otak, satu kolom chat. AI Lokal jalan tanpa internet tapi
                        // belum bisa baca foto struk; AI Cloud sebaliknya. Jarak tepi pil
                        // dikecilkan supaya keduanya tetap muat di baris judul.
                        EditorialPill(
                            text = "AI Lokal",
                            selected = aiMode == AiMode.LOCAL,
                            onClick = { viewModel.setAiMode(AiMode.LOCAL) },
                            contentPadding = Spacing.sm,
                            testTag = "chat_mode_local_chip"
                        )
                        EditorialPill(
                            text = "AI Cloud",
                            selected = aiMode == AiMode.GEMINI,
                            onClick = { viewModel.setAiMode(AiMode.GEMINI) },
                            contentPadding = Spacing.sm,
                            testTag = "chat_mode_gemini_chip"
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(ForestGreen, CircleShape)
                        )
                        Eyebrow(text = "Aktif & Siap Membantu", color = ForestGreen)
                    }
                }
            }

            HairlineDivider()



            val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
            val pendingReceiptConfirmation by viewModel.pendingReceiptConfirmation.collectAsState()
            val hasPending = pendingConfirmation != null || pendingReceiptConfirmation != null

            // Saat ada transaksi yang menunggu diperiksa, daftar pesan diganti
            // lembar konfirmasi. Cuma ada SATU area bergulir: dulu isi kartu
            // digulir di dalam daftar pesan, jadi gulirnya bersarang dan sebagian
            // isian tersembunyi.
            if (hasPending) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        pendingConfirmation?.let { pending ->
                            PendingTransactionSheet(
                                pending = pending,
                                viewModel = viewModel,
                                onPickDate = { showPendingDatePicker = true }
                            )
                        }
                        pendingReceiptConfirmation?.let { receipt ->
                            PendingReceiptSheet(
                                receipt = receipt,
                                viewModel = viewModel,
                                onPickDate = { showReceiptDatePicker = true }
                            )
                        }
                    }

                    // Tombol keputusan tidak ikut tergulir supaya selalu terlihat
                    // dan tetap menempel di bawah lembarnya.
                    if (pendingConfirmation != null) {
                        ConfirmFooter(
                            onCancel = { viewModel.cancelPendingTransaction() },
                            onConfirm = { viewModel.confirmPendingTransaction() },
                            confirmTag = "confirm_yes_button",
                            cancelTag = "confirm_no_button"
                        )
                    } else if (pendingReceiptConfirmation != null) {
                        ConfirmFooter(
                            onCancel = { viewModel.cancelPendingReceipt() },
                            onConfirm = { viewModel.confirmPendingReceipt() },
                            confirmTag = "confirm_receipt_yes_button",
                            cancelTag = "confirm_receipt_no_button"
                        )
                    }
                }
            } else {
                ChatMessageList(
                    messages = messages,
                    isAiProcessing = isAiProcessing,
                    listState = listState,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }

            HairlineDivider()

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CreamRaised)
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
                        .background(Hairline)
                        .testTag("chat_receipt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Pindai Struk Belanja",
                        tint = if (receiptDisabled) InkMuted else Ink
                    )
                }

                OutlinedTextField(
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
                            color = InkMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    // Gaya kolom isian editorial: latar krem, bingkai rambut yang
                    // menyala jingga saat fokus â€” sama seperti kolom di dialog.
                    colors = editorialFieldColors(),
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
                                Hairline
                            } else {
                                Ink
                            }
                        )
                        .testTag("chat_send_button"),
                    enabled = !hasPending && !isAiProcessing && inputText.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputText.trim().isEmpty() || hasPending || isAiProcessing) {
                            InkMuted
                        } else {
                            Cream
                        }
                    )
                }
            }
        }
    }
}



/** Daftar pesan chat. Dipisah dari [ChatScreenContent] karena saat ada
 *  transaksi menunggu diperiksa, daftar ini diganti lembar konfirmasi. */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isAiProcessing: Boolean,
    listState: LazyListState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
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
                            color = InkMuted,
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
}

/**
 * Lembar pemeriksaan transaksi hasil pembacaan AI.
 *
 * Semua isian selalu terbuka dan berlabel. Sebelumnya kartu ini punya mode
 * ringkas/Ubah yang harus diketuk dulu, isiannya cuma ditandai ikon tanpa teks,
 * dan jenis transaksi ditandai satu chip yang tampak seperti label padahal bisa
 * ditekan. Sekarang bentuknya mengikuti form transaksi di aplikasi: label di
 * atas tiap isian, nominal paling menonjol, pemilih jenis dua pil terbagi rata.
 */
@Composable
private fun PendingTransactionSheet(
    pending: ExtractionResult,
    viewModel: ChatViewModel,
    onPickDate: () -> Unit
) {
    val isIncome = pending.type?.lowercase() == "income" || pending.type?.lowercase() == "pemasukan"
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val cats = if (isIncome) incomeCategories else expenseCategories
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_edit_transaction_card"),
        shape = RoundedCornerShape(CornerRadius.bezelCore),
        colors = CardDefaults.cardColors(containerColor = CardCore),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Eyebrow(text = "Periksa transaksi")

            // Jenis: dua pilihan terbagi rata, yang aktif terlihat jelas.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                EditorialPill(
                    text = "Pengeluaran",
                    selected = !isIncome,
                    onClick = { viewModel.updatePendingTransaction(pending.copy(type = "expense")) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.labelLarge,
                    contentPadding = Spacing.sm
                )
                EditorialPill(
                    text = "Pemasukan",
                    selected = isIncome,
                    onClick = { viewModel.updatePendingTransaction(pending.copy(type = "income")) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.labelLarge,
                    contentPadding = Spacing.sm
                )
            }

            // Nominal jadi isian paling menonjol: angka serif besar di tengah.
            LabeledField(label = "Jumlah (Rp)") {
                val amtStr = if (pending.amount == null || pending.amount == 0L) "" else FormatUtils.formatInputNumber(pending.amount.toString())
                val amtFieldValue = remember(amtStr) { TextFieldValue(amtStr, TextRange(amtStr.length)) }
                BasicTextField(
                    value = amtFieldValue,
                    onValueChange = { field ->
                        val clean = field.text.replace(".", "")
                        if (clean.length <= 11 && (clean.isEmpty() || clean.all { it.isDigit() })) {
                            viewModel.updatePendingTransaction(pending.copy(amount = clean.toLongOrNull() ?: 0L))
                        }
                    },
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = Ink, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(BurntOrange),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    // Nomor kosong tetap butuh penanda, kalau tidak isiannya
                    // kelihatan seperti garis kosong tanpa arti.
                    decorationBox = { isian ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (amtFieldValue.text.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.displaySmall.copy(color = InkMuted)
                                )
                            }
                            isian()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                        .testTag("pending_transaction_amount_input")
                )
            }

            DashedDivider()

            // Urutan isian detail: deskripsi - kategori - tanggal - catatan.
            LabeledField(label = "Deskripsi") {
                EditorialTextField(
                    value = pending.description ?: "",
                    onValueChange = { if (it.length <= 20) viewModel.updatePendingTransaction(pending.copy(description = it)) },
                    placeholder = "cth: Beli eskrim",
                    testTag = "pending_transaction_description_input"
                )
            }

            LabeledField(label = "Kategori") {
                Box {
                    TapField(
                        text = pending.category ?: "Pilih kategori",
                        onClick = { categoryDropdownExpanded = true },
                        testTag = "pending_transaction_category_field"
                    )
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.background(CreamRaised).border(1.dp, Hairline)
                    ) {
                        cats.forEach { categoryLabel ->
                            val (_, label) = CategoryIconMapper.extractEmojiAndLabel(categoryLabel)
                            DropdownMenuItem(
                                text = { Text(categoryLabel, color = Ink) },
                                onClick = {
                                    viewModel.updatePendingTransaction(pending.copy(category = label))
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            LabeledField(label = "Tanggal") {
                TapField(
                    text = FormatUtils.formatDate(pending.date ?: System.currentTimeMillis()),
                    onClick = onPickDate,
                    testTag = "pending_transaction_date_field"
                )
            }

            LabeledField(label = "Catatan (opsional)") {
                EditorialTextField(
                    value = pending.notes ?: "",
                    onValueChange = { if (it.length <= 100) viewModel.updatePendingTransaction(pending.copy(notes = it)) },
                    placeholder = "Ketik catatan...",
                    testTag = "pending_transaction_notes_input"
                )
            }
        }
    }
}

/**
 * Lembar pemeriksaan hasil pindai struk.
 *
 * Sama seperti lembar transaksi: selalu terbuka, tiap isian berlabel. Tiap
 * kelompok belanja tetap bisa dibuka-tutup karena struk bisa berisi banyak
 * kelompok, tapi kepala kelompoknya sekarang menampilkan kategori dan nominal
 * sekaligus supaya tidak perlu dibuka untuk tahu isinya.
 */
@Composable
private fun PendingReceiptSheet(
    receipt: ParsedReceipt,
    viewModel: ChatViewModel,
    onPickDate: () -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    var expandedGroups by remember { mutableStateOf(setOf<Int>()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_edit_card"),
        shape = RoundedCornerShape(CornerRadius.bezelCore),
        colors = CardDefaults.cardColors(containerColor = CardCore),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Eyebrow(text = "Periksa struk belanja")
            Text(
                text = "${receipt.grouped.size} kelompok transaksi  â€¢  Total ${FormatUtils.formatRupiah(receipt.total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )

            LabeledField(label = "Nama struk") {
                EditorialTextField(
                    value = receipt.title,
                    onValueChange = { viewModel.updatePendingReceipt(receipt.copy(title = it)) },
                    placeholder = "cth: Indomaret",
                    testTag = "receipt_title_input"
                )
            }

            LabeledField(label = "Tanggal") {
                TapField(
                    text = FormatUtils.formatDate(receipt.dateMillis),
                    onClick = onPickDate,
                    testTag = "receipt_date_field"
                )
            }

            DashedDivider()

            Eyebrow(text = "Rincian transaksi")

            receipt.grouped.forEachIndexed { index, grp ->
                val isExpanded = expandedGroups.contains(index)
                val isItemIncome = grp.type.lowercase() == "income" || grp.type.lowercase() == "pemasukan"
                val itemCats = if (isItemIncome) incomeCategories else expenseCategories
                var itemCategoryDropdownExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.md))
                        .background(CreamRaised)
                        .border(1.dp, Hairline, RoundedCornerShape(CornerRadius.md))
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Kepala kelompok: kategori + nominal, sekaligus tombol
                    // buka-tutup. Tanpa dibuka, isinya sudah terbaca.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = if (isExpanded) "Tutup rincian" else "Ubah rincian"
                            ) {
                                expandedGroups = if (isExpanded) expandedGroups - index else expandedGroups + index
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = grp.category.ifBlank { "Tanpa kategori" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                                maxLines = 1
                            )
                            if (grp.description.isNotBlank()) {
                                Text(
                                    text = grp.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = FormatUtils.formatRupiah(grp.amount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Sembunyikan rincian" else "Tampilkan rincian",
                            tint = InkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            EditorialPill(
                                text = "Pengeluaran",
                                selected = !isItemIncome,
                                onClick = {
                                    val updated = receipt.grouped.toMutableList()
                                    updated[index] = grp.copy(type = "expense")
                                    viewModel.updatePendingReceipt(receipt.copy(grouped = updated))
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.labelLarge,
                                contentPadding = Spacing.sm
                            )
                            EditorialPill(
                                text = "Pemasukan",
                                selected = isItemIncome,
                                onClick = {
                                    val updated = receipt.grouped.toMutableList()
                                    updated[index] = grp.copy(type = "income")
                                    viewModel.updatePendingReceipt(receipt.copy(grouped = updated))
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.labelLarge,
                                contentPadding = Spacing.sm
                            )
                        }

                        LabeledField(label = "Kategori") {
                            Box {
                                TapField(
                                    text = grp.category.ifBlank { "Pilih kategori" },
                                    onClick = { itemCategoryDropdownExpanded = true },
                                    testTag = "receipt_item_category_field_$index"
                                )
                                DropdownMenu(
                                    expanded = itemCategoryDropdownExpanded,
                                    onDismissRequest = { itemCategoryDropdownExpanded = false },
                                    modifier = Modifier.background(CreamRaised).border(1.dp, Hairline)
                                ) {
                                    itemCats.forEach { categoryLabel ->
                                        val (_, label) = CategoryIconMapper.extractEmojiAndLabel(categoryLabel)
                                        DropdownMenuItem(
                                            text = { Text(categoryLabel, color = Ink) },
                                            onClick = {
                                                val updated = receipt.grouped.toMutableList()
                                                updated[index] = grp.copy(category = label)
                                                viewModel.updatePendingReceipt(receipt.copy(grouped = updated))
                                                itemCategoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        LabeledField(label = "Jumlah (Rp)") {
                            val grpAmtStr = if (grp.amount == 0L) "" else FormatUtils.formatInputNumber(grp.amount.toString())
                            EditorialTextField(
                                value = grpAmtStr,
                                onValueChange = { field ->
                                    val clean = field.replace(".", "")
                                    if (clean.length <= 11 && (clean.isEmpty() || clean.all { it.isDigit() })) {
                                        val updated = receipt.grouped.toMutableList()
                                        updated[index] = grp.copy(amount = clean.toLongOrNull() ?: 0L)
                                        viewModel.updatePendingReceipt(
                                            receipt.copy(grouped = updated, total = updated.sumOf { it.amount })
                                        )
                                    }
                                },
                                placeholder = "0",
                                testTag = "receipt_item_amount_input_$index",
                                keyboardType = KeyboardType.Number
                            )
                        }

                        LabeledField(label = "Deskripsi") {
                            EditorialTextField(
                                value = grp.description,
                                onValueChange = { newValue ->
                                    if (newValue.length <= 20) {
                                        val updated = receipt.grouped.toMutableList()
                                        updated[index] = grp.copy(description = newValue)
                                        viewModel.updatePendingReceipt(receipt.copy(grouped = updated))
                                    }
                                },
                                placeholder = "cth: Susu UHT",
                                testTag = "receipt_item_description_input_$index"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tombol keputusan lembar konfirmasi. Selalu terlihat, tidak ikut tergulir. */
@Composable
private fun ConfirmFooter(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmTag: String,
    cancelTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CreamRaised)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        EditorialButton(
            text = "Ya, Simpan",
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            // Separuh lebar: panah dimatikan supaya label tetap satu baris.
            trailingArrow = false,
            testTag = confirmTag
        )
        EditorialButton(
            text = "Batal",
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            style = EditorialButtonStyle.Ghost,
            trailingArrow = false,
            testTag = cancelTag
        )
    }
}

/** Isian berlabel, isian teks, dan isian ketuk dipakai bersama dengan dialog
 * editorial; definisinya pindah ke `ui/components/Editorial.kt` supaya tab Chat
 * dan dialog tidak pernah punya dua bentuk isian yang berbeda. */

