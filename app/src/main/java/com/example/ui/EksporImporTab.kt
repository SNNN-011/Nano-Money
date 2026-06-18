package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import com.example.util.BackupHelper
import com.example.util.BackupScheduler
import com.example.util.GoogleDriveHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import java.io.File
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import com.example.ui.theme.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthSelectChip(
    month: YearMonth,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")) }
    val formattedName = remember(month) { month.format(formatter) }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("month_chip_${month.year}_${month.monthValue}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SteelBlue.copy(alpha = 0.25f) else TranslucentGlass
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = if (isSelected) {
                    listOf(SteelBlue.copy(alpha = 0.6f), SteelBlue.copy(alpha = 0.2f))
                } else {
                    listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                }
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formattedName,
                color = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Dipilih" else "Tidak dipilih",
                tint = if (isSelected) SteelBlue else GhostWhite.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EksporImporTabContent(
    isWideScreen: Boolean,
    availableMonths: List<YearMonth>,
    onImportCsvFile: () -> Unit,
    onExportCsvFile: (List<YearMonth>) -> Unit,
    onExportPdfFile: (List<YearMonth>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Monthly selection states
    var selectedMonths by remember(availableMonths) { mutableStateOf(availableMonths.toSet()) }
    var isMonthsExpanded by remember { mutableStateOf(false) }

    // Security preferences and states
    val securityPrefs = remember { context.getSharedPreferences("app_security_prefs", android.content.Context.MODE_PRIVATE) }
    var isPinEnabled by remember { mutableStateOf(securityPrefs.getBoolean("pin_enabled", false)) }
    var savedPin by remember { mutableStateOf(securityPrefs.getString("saved_pin", "") ?: "") }
    var isBiometricEnabled by remember { mutableStateOf(securityPrefs.getBoolean("biometric_enabled", false)) }


    // Pin setups
    var isSetPinDialogOpen by remember { mutableStateOf(false) }
    var pinInputText by remember { mutableStateOf("") }
    val securityQuestions = remember {
        listOf(
            "Apa nama hewan peliharaan pertama Anda?",
            "Apa nama kota tempat Anda lahir?",
            "Apa nama sekolah dasar (SD) Anda?",
            "Siapa nama guru favorit masa kecil Anda?"
        )
    }
    var selectedQuestionIndex by remember { mutableStateOf(0) }
    var securityAnswerInput by remember { mutableStateOf("") }
    var isQuestionDropdownExpanded by remember { mutableStateOf(false) }
    var pinDialogErrorText by remember { mutableStateOf("") }

    // Backup & Cloud sync states
    var isAutoBackupEnabled by remember { mutableStateOf(securityPrefs.getBoolean("auto_backup_enabled", false)) }
    var autoBackupInterval by remember { mutableStateOf(securityPrefs.getString("auto_backup_interval", "daily") ?: "daily") }
    var autoBackupHour by remember { mutableStateOf(securityPrefs.getInt("auto_backup_hour", 2)) }
    var autoBackupMinute by remember { mutableStateOf(securityPrefs.getInt("auto_backup_minute", 0)) }
    var autoBackupDayOfWeek by remember { mutableStateOf(securityPrefs.getInt("auto_backup_day_of_week", java.util.Calendar.SUNDAY)) }
    var backupList by remember { mutableStateOf(emptyList<File>()) }
    var selectedBackupToRestore by remember { mutableStateOf<File?>(null) }

    var connectedGoogleAccount by remember { mutableStateOf(GoogleDriveHelper.getSignedInAccount(context)) }
    var isDriveProcessing by remember { mutableStateOf(false) }
    var driveBackupList by remember { mutableStateOf<List<GoogleDriveHelper.DriveBackupFile>>(emptyList()) }
    var selectedDriveBackupToRestore by remember { mutableStateOf<GoogleDriveHelper.DriveBackupFile?>(null) }
    var selectedLocalBackupToUpload by remember { mutableStateOf<File?>(null) }
    var driveErrorDetailMessage by remember { mutableStateOf<String?>(null) }

    // Google SignIn Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            connectedGoogleAccount = account
            coroutineScope.launch {
                val firebaseResult = com.example.util.FirebaseSyncHelper.signInWithGoogleInFirebase(context, account)
                if (firebaseResult.isSuccess) {
                    Toast.makeText(context, "Berhasil menghubungkan Google Drive & sinkronisasi cloud!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Google Drive terhubung, tetapi server sinkronisasi gagal: ${firebaseResult.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            android.util.Log.e("GoogleDrive", "Google Sign-In failed: StatusCode=$statusCode", e)
            if (statusCode == 12501) {
                Toast.makeText(context, "Batal menghubungkan Google Drive.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal masuk: ${e.localizedMessage ?: statusCode}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleDrive", "Google Sign-In error", e)
            Toast.makeText(context, "Kesalahan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Google Drive Sync effects
    LaunchedEffect(connectedGoogleAccount) {
        if (connectedGoogleAccount != null) {
            isDriveProcessing = true
            when (val result = GoogleDriveHelper.listBackupsFromDrive(context)) {
                is GoogleDriveHelper.DriveResult.Success -> {
                    driveBackupList = result.data
                }
                is GoogleDriveHelper.DriveResult.Error -> {
                    android.util.Log.e("GoogleDrive", "Gagal memuat daftar Drive: ${result.message}")
                    driveErrorDetailMessage = result.message
                    if (result.recoverableIntent != null) {
                        try {
                            context.startActivity(result.recoverableIntent)
                        } catch (e: Exception) {
                            android.util.Log.e("GoogleDrive", "Failed to launch auth recovery", e)
                        }
                    }
                }
            }
            isDriveProcessing = false
        } else {
            driveBackupList = emptyList()
        }
    }

    // Fetch local backups
    LaunchedEffect(Unit) {
        backupList = BackupHelper.getBackups(context)
    }

    // BODY LAYOUT
    val contentBody = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Description
            Column {
                Text(
                    text = "Laporan & Ekspor Data",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = GhostWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ekspor laporan keuangan resmi ke berkas PDF atau ekspor data mentah transaksi Anda ke lembar kerja Excel (.csv) sesuai bulan yang dipilih.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = GhostWhite.copy(alpha = 0.5f)
                )
            }

            // Exporter Section Card
            UnifiedExportCard(
                isWideScreen = isWideScreen,
                availableMonths = availableMonths,
                selectedMonths = selectedMonths,
                onSelectedMonthsChanged = { selectedMonths = it },
                isMonthsExpanded = isMonthsExpanded,
                onIsMonthsExpandedChanged = { isMonthsExpanded = it },
                onExportPdfFile = onExportPdfFile,
                onExportCsvFile = onExportCsvFile,
                onImportCsvFile = onImportCsvFile
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Security Controls Section Card
            SecuritySettingsSection(
                isPinEnabled = isPinEnabled,
                onPinToggle = { checked ->
                    if (!checked) {
                        isPinEnabled = false
                        isBiometricEnabled = false
                        securityPrefs.edit()
                            .putBoolean("pin_enabled", false)
                            .putBoolean("biometric_enabled", false)
                            .commit()
                        Toast.makeText(context, "Kunci PIN dinonaktifkan", Toast.LENGTH_SHORT).show()
                    } else {
                        val storedPin = securityPrefs.getString("saved_pin", "") ?: ""
                        val storedQuestion = securityPrefs.getString("security_question", "") ?: ""
                        val storedAnswer = securityPrefs.getString("security_answer", "") ?: ""
                        
                        if (storedPin.isNotEmpty() && storedAnswer.isNotEmpty()) {
                            isPinEnabled = true
                            securityPrefs.edit()
                                .putBoolean("pin_enabled", true)
                                .commit()
                            Toast.makeText(context, "Kunci PIN diaktifkan", Toast.LENGTH_SHORT).show()
                        } else {
                            pinInputText = ""
                            securityAnswerInput = ""
                            pinDialogErrorText = ""
                            selectedQuestionIndex = 0
                            isSetPinDialogOpen = true
                        }
                    }
                },
                isBiometricEnabled = isBiometricEnabled,
                onBiometricToggle = { checked ->
                    isBiometricEnabled = checked
                    securityPrefs.edit().putBoolean("biometric_enabled", checked).commit()
                },
                onEditSecurity = {
                    val storedPin = securityPrefs.getString("saved_pin", "") ?: ""
                    val storedQuestion = securityPrefs.getString("security_question", "") ?: ""
                    val storedAnswer = securityPrefs.getString("security_answer", "") ?: ""
                    
                    pinInputText = storedPin
                    securityAnswerInput = storedAnswer
                    pinDialogErrorText = ""
                    selectedQuestionIndex = securityQuestions.indexOf(storedQuestion).let { if (it == -1) 0 else it }
                    isSetPinDialogOpen = true
                }
            )



            Spacer(modifier = Modifier.height(10.dp))

            // Database Backups and Google Drive sync card
            DatabaseBackupSection(
                isAutoBackupEnabled = isAutoBackupEnabled,
                onAutoBackupEnabledChanged = { enabled ->
                    isAutoBackupEnabled = enabled
                    securityPrefs.edit().putBoolean("auto_backup_enabled", enabled).commit()
                },
                autoBackupInterval = autoBackupInterval,
                onAutoBackupIntervalChanged = { interval ->
                    autoBackupInterval = interval
                    securityPrefs.edit().putString("auto_backup_interval", interval).commit()
                },
                autoBackupHour = autoBackupHour,
                onAutoBackupHourChanged = { hour ->
                    autoBackupHour = hour
                    securityPrefs.edit().putInt("auto_backup_hour", hour).commit()
                },
                autoBackupMinute = autoBackupMinute,
                onAutoBackupMinuteChanged = { minute ->
                    autoBackupMinute = minute
                    securityPrefs.edit().putInt("auto_backup_minute", minute).commit()
                },
                autoBackupDayOfWeek = autoBackupDayOfWeek,
                onAutoBackupDayOfWeekChanged = { dayOfWeek ->
                    autoBackupDayOfWeek = dayOfWeek
                    securityPrefs.edit().putInt("auto_backup_day_of_week", dayOfWeek).commit()
                },
                backupList = backupList,
                onBackupListChanged = { backupList = it },
                connectedGoogleAccount = connectedGoogleAccount,
                onConnectedGoogleAccountChanged = { connectedGoogleAccount = it },
                isDriveProcessing = isDriveProcessing,
                onIsDriveProcessingChanged = { isDriveProcessing = it },
                driveBackupList = driveBackupList,
                onDriveBackupListChanged = { driveBackupList = it },
                selectedBackupToRestore = selectedBackupToRestore,
                onSelectedBackupToRestoreChange = { selectedBackupToRestore = it },
                selectedDriveBackupToRestore = selectedDriveBackupToRestore,
                onSelectedDriveBackupToRestoreChange = { selectedDriveBackupToRestore = it },
                selectedLocalBackupToUpload = selectedLocalBackupToUpload,
                onSelectedLocalBackupToUploadChange = { selectedLocalBackupToUpload = it },
                driveErrorDetailMessage = driveErrorDetailMessage,
                onDriveErrorDetailMessageChange = { driveErrorDetailMessage = it },
                onConnectGoogleDrive = {
                    val client = GoogleDriveHelper.getGoogleSignInClient(context)
                    googleSignInLauncher.launch(client.signInIntent)
                }
            )

            // DIALOGS & OVERLAYS

            // 1. Confirm Restore Local Backup Dialog
            if (selectedBackupToRestore != null) {
                Dialog(onDismissRequest = { selectedBackupToRestore = null }) {
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
                                    text = "Konfirmasi Pemulihan",
                                    color = GhostWhite,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Apakah Anda yakin ingin memulihkan database dari salinan cadangan ini? Seluruh data catatan keuangan aktif saat ini akan ditimpa dengan data cadangan.\n\nSetelah klik pulihkan, aplikasi akan memuat ulang data.",
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PremiumButton(
                                        text = "Batal",
                                        onClick = { selectedBackupToRestore = null },
                                        isActive = false,
                                        modifier = Modifier.weight(1f),
                                        testTag = "cancel_restore_local_backup"
                                    )
                                    PremiumButton(
                                        text = "Pulihkan & Mulai Ulang",
                                        onClick = {
                                            val backupToRestore = selectedBackupToRestore
                                            selectedBackupToRestore = null
                                            if (backupToRestore != null) {
                                                val success = BackupHelper.restoreBackup(context, backupToRestore)
                                                if (success) {
                                                    Toast.makeText(context, "Database berhasil dipulihkan!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Gagal memulihkan database", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        isActive = true,
                                        modifier = Modifier.weight(1.5f),
                                        testTag = "confirm_restore_local_backup"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Google Drive Upload Local Backup Dialog
            if (selectedLocalBackupToUpload != null) {
                Dialog(onDismissRequest = { selectedLocalBackupToUpload = null }) {
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
                                    text = "Unggah ke Google Drive",
                                    color = GhostWhite,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Apakah Anda yakin ingin mengunggah berkas cadangan '${selectedLocalBackupToUpload?.name}' ke Google Drive Anda? Berkas ini akan tersimpan aman dari kehilangan lokal.",
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PremiumButton(
                                        text = "Batal",
                                        onClick = { selectedLocalBackupToUpload = null },
                                        isActive = false,
                                        modifier = Modifier.weight(1f),
                                        testTag = "cancel_upload_to_drive"
                                    )
                                    PremiumButton(
                                        text = "Unggah",
                                        onClick = {
                                            val localFile = selectedLocalBackupToUpload
                                            selectedLocalBackupToUpload = null
                                            if (localFile != null) {
                                                coroutineScope.launch {
                                                    isDriveProcessing = true
                                                    when (val res = GoogleDriveHelper.uploadBackupToDrive(context, localFile)) {
                                                        is GoogleDriveHelper.DriveResult.Success -> {
                                                            Toast.makeText(context, "Sukses mengunggah cadangan ke Google Drive!", Toast.LENGTH_SHORT).show()
                                                            val listRes = GoogleDriveHelper.listBackupsFromDrive(context)
                                                            if (listRes is GoogleDriveHelper.DriveResult.Success) {
                                                                driveBackupList = listRes.data
                                                            }
                                                        }
                                                        is GoogleDriveHelper.DriveResult.Error -> {
                                                            driveErrorDetailMessage = res.message
                                                        }
                                                    }
                                                    isDriveProcessing = false
                                                }
                                            }
                                        },
                                        isActive = true,
                                        modifier = Modifier.weight(1.2f),
                                        testTag = "confirm_upload_to_drive"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Google Drive Cloud Backup Restore Dialog
            if (selectedDriveBackupToRestore != null) {
                Dialog(onDismissRequest = { selectedDriveBackupToRestore = null }) {
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
                                    text = "Pulihkan dari Google Drive",
                                    color = GhostWhite,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Perhatian: Mengunduh dan memulihkan berkas dari Google Drive akan menimpa seluruh database lokal aktif Anda dengan data cadangan cloud ini.\n\nAplikasi akan mengunduh berkas, menuliskannya, dan secara otomatis memuat ulang saat pemulihan berhasil. Apakah Anda ingin melanjutkan?",
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PremiumButton(
                                        text = "Batal",
                                        onClick = { selectedDriveBackupToRestore = null },
                                        isActive = false,
                                        modifier = Modifier.weight(1f),
                                        testTag = "cancel_restore_from_drive"
                                    )
                                    PremiumButton(
                                        text = "Unduh & Pulihkan",
                                        onClick = {
                                            val driveFile = selectedDriveBackupToRestore
                                            selectedDriveBackupToRestore = null
                                            if (driveFile != null) {
                                                coroutineScope.launch {
                                                    isDriveProcessing = true
                                                    val localTargetDir = BackupHelper.getBackupDirectory(context)
                                                    val localTempFile = File(localTargetDir, "temp_cloud_restore_${driveFile.name}")
                                                    
                                                    when (val downloadRes = GoogleDriveHelper.downloadBackupFromDrive(context, driveFile.id, localTempFile)) {
                                                        is GoogleDriveHelper.DriveResult.Success -> {
                                                            val successRestore = BackupHelper.restoreBackup(context, localTempFile)
                                                            localTempFile.delete()
                                                            if (successRestore) {
                                                                Toast.makeText(context, "Database berhasil dipulihkan dari Cloud!", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "Gagal menimpa database dengan berkas hasil unduhan.", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        is GoogleDriveHelper.DriveResult.Error -> {
                                                            driveErrorDetailMessage = downloadRes.message
                                                        }
                                                    }
                                                    isDriveProcessing = false
                                                }
                                            }
                                        },
                                        isActive = true,
                                        modifier = Modifier.weight(1.5f),
                                        testTag = "confirm_restore_from_drive"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Google Drive Error Detail Alert Dialog
            if (driveErrorDetailMessage != null) {
                val errorMsg = driveErrorDetailMessage ?: ""
                val extractedUrl = remember(errorMsg) { 
                    errorMsg.split(" ", "\n", "\t").firstOrNull { it.startsWith("http://") || it.startsWith("https://") } 
                }

                Dialog(onDismissRequest = { driveErrorDetailMessage = null }) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Masalah Google Drive",
                                        color = GhostWhite,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Berikut detail masalah yang terjadi saat memproses Google Drive Anda:",
                                        color = GhostWhite.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                                            .border(1.dp, GhostWhite.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = errorMsg,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            ),
                                            color = Color(0xFFFFCC80)
                                        )
                                    }

                                    if (errorMsg.contains("is disabled") || errorMsg.contains("has not been used")) {
                                        Text(
                                            text = "💡 Petunjuk: Layanan Google Drive API belum diaktifkan di Google Cloud Project dari aplikasi ini. Salin atau ketuk tombol di bawah untuk membukanya dan mengaktifkan tombol 'Enable Google Drive API' di konsol pengembang Google.",
                                            color = SteelBlue,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (extractedUrl != null) {
                                        PremiumButton(
                                            text = "Buka Tautan Google Console",
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(extractedUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Gagal membuka web browser.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            isActive = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            testTag = "open_google_console"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        PremiumButton(
                                            text = "Salin Error",
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("GoogleDriveError", errorMsg)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Detail error disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                                            },
                                            isActive = false,
                                            modifier = Modifier.weight(1f),
                                            testTag = "copy_error_details"
                                        )

                                        PremiumButton(
                                            text = "Tutup",
                                            onClick = { driveErrorDetailMessage = null },
                                            isActive = true,
                                            modifier = Modifier.weight(1f),
                                            testTag = "close_error_dialog"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. SET PIN DIALOG
            if (isSetPinDialogOpen) {
                Dialog(onDismissRequest = { isSetPinDialogOpen = false }) {
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
                                    text = "Atur PIN Kunci & Pemulihan",
                                    color = GhostWhite,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Masukkan 4 angka kode PIN baru Anda:", color = GhostWhite.copy(alpha = 0.7f), fontSize = 13.sp)
                                    OutlinedTextField(
                                        value = pinInputText,
                                        onValueChange = { newValue ->
                                            if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                                                pinInputText = newValue
                                            }
                                        },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = GhostWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("••••", color = GhostWhite.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SteelBlue,
                                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                            focusedTextColor = GhostWhite
                                        )
                                    )

                                    HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                                    Text("Pilih pertanyaan keamanan untuk bantuan pemulihan jika Anda lupa PIN:", color = GhostWhite.copy(alpha = 0.7f), fontSize = 13.sp)
                                    
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = securityQuestions[selectedQuestionIndex],
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier.fillMaxWidth().clickable { isQuestionDropdownExpanded = true },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SteelBlue,
                                                unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                                focusedTextColor = GhostWhite,
                                                unfocusedTextColor = GhostWhite
                                            ),
                                            trailingIcon = {
                                                IconButton(onClick = { isQuestionDropdownExpanded = !isQuestionDropdownExpanded }) {
                                                    Icon(
                                                        imageVector = if (isQuestionDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = SteelBlue
                                                    )
                                                }
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = isQuestionDropdownExpanded,
                                            onDismissRequest = { isQuestionDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth().background(MidnightAbyss)
                                        ) {
                                            securityQuestions.forEachIndexed { index, question ->
                                                DropdownMenuItem(
                                                    text = { Text(question, color = GhostWhite, fontSize = 12.sp) },
                                                    onClick = {
                                                        selectedQuestionIndex = index
                                                        isQuestionDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    OutlinedTextField(
                                        value = securityAnswerInput,
                                        onValueChange = { if (it.length <= 30) securityAnswerInput = it },
                                        placeholder = { Text("Jawaban Anda...", color = GhostWhite.copy(alpha = 0.3f)) },
                                        label = { Text("Jawaban Pemulihan PIN", color = GhostWhite.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SteelBlue,
                                            unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                            focusedTextColor = GhostWhite,
                                            unfocusedTextColor = GhostWhite
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (pinDialogErrorText.isNotEmpty()) {
                                        Text(
                                            pinDialogErrorText,
                                            color = Color.Red,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PremiumButton(
                                        text = "Batal",
                                        onClick = { isSetPinDialogOpen = false },
                                        isActive = false,
                                        modifier = Modifier.weight(1f),
                                        testTag = "cancel_set_pin"
                                    )
                                    PremiumButton(
                                        text = "Simpan",
                                        onClick = {
                                            if (pinInputText.length != 4) {
                                                pinDialogErrorText = "PIN harus terdiri dari 4 digit angka!"
                                            } else if (securityAnswerInput.trim().isEmpty()) {
                                                pinDialogErrorText = "Jawaban pemulihan tidak boleh kosong!"
                                            } else {
                                                isPinEnabled = true
                                                savedPin = pinInputText
                                                securityPrefs.edit()
                                                    .putBoolean("pin_enabled", true)
                                                    .putString("saved_pin", pinInputText)
                                                    .putString("security_question", securityQuestions[selectedQuestionIndex])
                                                    .putString("security_answer", securityAnswerInput.trim().lowercase())
                                                    .apply()
                                                isSetPinDialogOpen = false
                                                Toast.makeText(context, "Kunci PIN berhasil diatur!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        isActive = true,
                                        modifier = Modifier.weight(1f),
                                        testTag = "confirm_set_pin"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // MAIN SCROLL WRAPPER
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isWideScreen) 840.dp else Float.MAX_VALUE.dp)
                .verticalScroll(rememberScrollState())
                .padding(if (isWideScreen) 24.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            contentBody()
        }
    }
}
