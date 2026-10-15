package com.example.ui.export

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
import com.example.data.FinancialRecord
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.animation.AnimatedVisibility
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.ui.components.GlassCard
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
            .clip(RoundedCornerShape(CornerRadius.md))
            .clickable { onClick() }
            .testTag("month_chip_${month.year}_${month.monthValue}"),
        shape = RoundedCornerShape(CornerRadius.md),
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
    deletedRecords: List<FinancialRecord> = emptyList(),
    onRestoreRecord: (FinancialRecord) -> Unit = {},
    onRestoreAllRecords: () -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Monthly selection states
    var selectedMonths by remember(availableMonths) { mutableStateOf(availableMonths.toSet()) }
    var isMonthsExpanded by remember { mutableStateOf(false) }

    val securityPrefs = remember { com.example.util.SecurePrefsHelper.getEncryptedPrefs(context, "app_security_prefs") }

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
                    com.example.util.FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
                } else {
                    Toast.makeText(context, "Google Drive terhubung, tetapi server sinkronisasi gagal: ${firebaseResult.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            com.example.util.SecureLog.e("GoogleDrive", "Google Sign-In failed: StatusCode=$statusCode", e)
            if (statusCode == 12501) {
                Toast.makeText(context, "Batal menghubungkan Google Drive.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal masuk: ${e.localizedMessage ?: statusCode}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            com.example.util.SecureLog.e("GoogleDrive", "Google Sign-In error", e)
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
                    com.example.util.SecureLog.e("GoogleDrive", "Gagal memuat daftar Drive: ${result.message}")
                    driveErrorDetailMessage = result.message
                    if (result.recoverableIntent != null) {
                        try {
                            context.startActivity(result.recoverableIntent)
                        } catch (e: Exception) {
                            com.example.util.SecureLog.e("GoogleDrive", "Failed to launch auth recovery", e)
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

            // Exporter, sinkronisasi realtime, dan cadangan Drive digabung dalam satu card.
            // Ekspor dirender lewat headerContent supaya tidak jadi card terpisah.
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
                },
                headerContent = {
                    UnifiedExportCard(
                        isWideScreen = isWideScreen,
                        availableMonths = availableMonths,
                        selectedMonths = selectedMonths,
                        onSelectedMonthsChanged = { selectedMonths = it },
                        isMonthsExpanded = isMonthsExpanded,
                        onIsMonthsExpandedChanged = { isMonthsExpanded = it },
                        onExportPdfFile = onExportPdfFile,
                        onExportCsvFile = onExportCsvFile,
                        onImportCsvFile = onImportCsvFile,
                        insideCard = true
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Keamanan (PIN) di atas Sampah: pengaturan ini lebih sering dibuka
            // daripada memulihkan transaksi yang terlanjung dihapus.
            // DIALOGS & OVERLAYS
            var showPinDialog by remember { mutableStateOf(false) }
            // H4a: mematikan PIN wajib verifikasi PIN lama dulu
            var showDisablePinDialog by remember { mutableStateOf(false) }
            var pinEnabled by remember { mutableStateOf(com.example.util.PinUtils.isPinEnabled(context)) }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("security_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.4f))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Keamanan",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = GhostWhite
                        )
                    }
Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pinEnabled) "PIN Aktif" else "PIN Belum Diaktifkan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pinEnabled) NeonGreen else GhostWhite.copy(alpha = 0.6f)
                        )
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinDialog = true
                                } else {
                                    // H4a: jangan matikan langsung — minta verifikasi PIN lama
                                    showDisablePinDialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GhostWhite,
                                checkedTrackColor = SteelBlue,
                                uncheckedThumbColor = GhostWhite.copy(alpha = 0.4f),
                                uncheckedTrackColor = GhostWhite.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.testTag("pin_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Kunci aplikasi dengan PIN untuk melindungi data Anda.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = GhostWhite.copy(alpha = 0.55f)
                    )

                    // Ubah PIN hanya muncul kalau PIN sudah aktif.
                    AnimatedVisibility(visible = pinEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CornerRadius.md))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            GhostWhite.copy(alpha = 0.06f),
                                            GhostWhite.copy(alpha = 0.02f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            GhostWhite.copy(alpha = 0.12f),
                                            GhostWhite.copy(alpha = 0.03f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(CornerRadius.md)
                                )
                                .clickable { showPinDialog = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LockReset,
                                    contentDescription = null,
                                    tint = SteelBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Ubah PIN",
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (showPinDialog) {
                com.example.ui.common.PinSetupDialog(
                    onDismiss = { showPinDialog = false },
                    onPinSet = {
                        showPinDialog = false
                        pinEnabled = com.example.util.PinUtils.isPinEnabled(context)
                    }
                )
            }

            // H4a: konfirmasi nonaktifkan PIN (verifikasi PIN lama di dalam dialog)
            if (showDisablePinDialog) {
                com.example.ui.common.PinDisableDialog(
                    onDismiss = { showDisablePinDialog = false },
                    onPinDisabled = {
                        showDisablePinDialog = false
                        pinEnabled = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sampah: transaksi yang dihapus masih bisa dipulihkan.
            TrashSection(
                deletedRecords = deletedRecords,
                onRestore = onRestoreRecord,
                onRestoreAll = onRestoreAllRecords,
                onEmptyTrash = onEmptyTrash
            )

            // DIALOGS & OVERLAYS

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
