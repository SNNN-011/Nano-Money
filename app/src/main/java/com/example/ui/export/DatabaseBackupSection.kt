package com.example.ui.export

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.config.FormatUtils
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BackupHelper
import com.example.util.BackupScheduler
import com.example.data.sync.SyncCursors
import com.example.data.sync.SyncManager
import com.example.util.GoogleDriveHelper
import com.example.util.NotificationScheduler
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import com.example.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale







@Composable
fun DatabaseBackupSection(
    isAutoBackupEnabled: Boolean,
    onAutoBackupEnabledChanged: (Boolean) -> Unit,
    autoBackupInterval: String,
    onAutoBackupIntervalChanged: (String) -> Unit,
    autoBackupHour: Int,
    onAutoBackupHourChanged: (Int) -> Unit,
    autoBackupMinute: Int,
    onAutoBackupMinuteChanged: (Int) -> Unit,
    autoBackupDayOfWeek: Int,
    onAutoBackupDayOfWeekChanged: (Int) -> Unit,
    connectedGoogleAccount: GoogleSignInAccount?,
    onConnectedGoogleAccountChanged: (GoogleSignInAccount?) -> Unit,
    isDriveProcessing: Boolean,
    onIsDriveProcessingChanged: (Boolean) -> Unit,
    driveBackupList: List<GoogleDriveHelper.DriveBackupFile>,
    onDriveBackupListChanged: (List<GoogleDriveHelper.DriveBackupFile>) -> Unit,
    selectedDriveBackupToRestore: GoogleDriveHelper.DriveBackupFile?,
    onSelectedDriveBackupToRestoreChange: (GoogleDriveHelper.DriveBackupFile?) -> Unit,
    driveErrorDetailMessage: String?,
    onDriveErrorDetailMessageChange: (String?) -> Unit,
    onConnectGoogleDrive: () -> Unit,
    // Konten tambahan yang dirender di bagian atas card yang sama, supaya ekspor
    // dan sinkronisasi bisa berada dalam satu kartu, bukan dua kartu bertumpuk.
    headerContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityPrefs = remember { com.example.util.SecurePrefsHelper.getEncryptedPrefs(context, "security_prefs") }
    var isDriveBackupEnabled by remember { mutableStateOf(securityPrefs.getBoolean("drive_backup_enabled", true)) }
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    var driveBackupFileToDelete by remember { mutableStateOf<GoogleDriveHelper.DriveBackupFile?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var firestoreSyncEnabled by remember { mutableStateOf(SyncCursors.isSyncEnabled(context)) }
    var showSyncWarning by remember { mutableStateOf(false) }
    var showDeleteCloudConfirm by remember { mutableStateOf(false) }
    var isDeletingCloud by remember { mutableStateOf(false) }
    var tempBackupHour by remember(autoBackupHour) { mutableStateOf(autoBackupHour) }
    var tempBackupMinute by remember(autoBackupMinute) { mutableStateOf(autoBackupMinute) }
    var tempBackupDayOfWeek by remember(autoBackupDayOfWeek) { mutableStateOf(autoBackupDayOfWeek) }

    // Menu Cadangan Google Drive. Sheet expandable supaya tidak memakan tempat di
    // halaman Simpan yang juga berisi Sampah dan Keamanan.
    var showDriveMenu by remember { mutableStateOf(false) }

    fun getDayNameIndonesian(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            java.util.Calendar.SUNDAY -> "Minggu"
            java.util.Calendar.MONDAY -> "Senin"
            java.util.Calendar.TUESDAY -> "Selasa"
            java.util.Calendar.WEDNESDAY -> "Rabu"
            java.util.Calendar.THURSDAY -> "Kamis"
            java.util.Calendar.FRIDAY -> "Jumat"
            java.util.Calendar.SATURDAY -> "Sabtu"
            else -> "Minggu"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // --- REALTIME FIRESTORE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("realtime_firestore_card"),
            shape = RoundedCornerShape(CornerRadius.card),
            colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                var showFirestoreHelpInfo by remember { mutableStateOf(false) }

                headerContent?.let { extra ->
                    HorizontalDivider(
                        color = GhostWhite.copy(alpha = 0.1f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    extra()
                    HorizontalDivider(
                        color = GhostWhite.copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(NeonViolet, NeonViolet.copy(alpha = 0.4f))
                                    ),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sinkronisasi Realtime",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite
                        )
                    }

                    IconButton(
                        onClick = { showFirestoreHelpInfo = !showFirestoreHelpInfo },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Panduan Informasi Sinkronisasi Realtime",
                            tint = if (showFirestoreHelpInfo) NeonViolet else GhostWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Sinkronkan catatan transaksi Anda secara otomatis ke cloud.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = GhostWhite.copy(alpha = 0.55f)
                )

                if (showFirestoreHelpInfo) {
                    Dialog(onDismissRequest = { showFirestoreHelpInfo = false }) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(GhostWhite.copy(alpha = 0.2f), GhostWhite.copy(alpha = 0.02f))
                                )
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(modifier = Modifier.background(TranslucentGlass)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                                ) {
                                    Text(
                                        text = "Panduan Sinkronisasi Realtime",
                                        color = GhostWhite,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Sinkronkan seluruh catatan transaksi finansial Anda secara aman ke database awan Firestore. Secara otomatis menyinkronkan data antar perangkat yang terhubung ke akun yang sama.\n\nHubungkan akun Google Anda untuk mengaktifkan sinkronisasi mutakhir secara realtime.",
                                        color = GhostWhite.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp
                                    )
                                    PremiumButton(
                                        text = "Tutup",
                                        onClick = { showFirestoreHelpInfo = false },
                                        isActive = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (connectedGoogleAccount == null) {
                    PremiumButton(
                        text = "HUBUNGKAN AKUN GOOGLE",
                        onClick = onConnectGoogleDrive,
                        isActive = true,
                        icon = Icons.Default.Cloud,
                        modifier = Modifier.fillMaxWidth().testTag("connect_firestore_button")
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Akun: ${connectedGoogleAccount.email ?: "Terhubung"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SteelBlue),
                        )
                        IconButton(
                            onClick = { showDisconnectConfirmation = true },
                            // Tanpa .size(...) agar area sentuh tetap standar 48dp.
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Putuskan Akun",
                                tint = NeonRed.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Toggle sinkronisasi: default MATI. Saat mati tidak ada data
                    // lokal yang dikirim ke cloud; menyalakan butuh konfirmasi.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TranslucentForm.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sinkronisasi Firestore",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GhostWhite
                            )
                            Text(
                                text = if (firestoreSyncEnabled) {
                                    "Aktif — data terkirim & tersinkron ke cloud"
                                } else {
                                    "Nonaktif — data tetap hanya di HP ini"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = GhostWhite.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = firestoreSyncEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showSyncWarning = true
                                } else {
                                    firestoreSyncEnabled = false
                                    SyncManager.disableSync(context)
                                    Toast.makeText(context, "Sinkronisasi dimatikan. Data di HP tidak dihapus.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonViolet,
                                checkedTrackColor = NeonViolet.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("firestore_sync_toggle")
                        )
                    }

                    PremiumButton(
                        text = if (isDeletingCloud) "MENGHAPUS DATA CLOUD..." else "HAPUS DATA CLOUD",
                        onClick = { showDeleteCloudConfirm = true },
                        isActive = !isDeletingCloud,
                        isDestructive = true,
                        modifier = Modifier.fillMaxWidth().testTag("delete_cloud_data_button")
                    )

                    val workManager = remember { WorkManager.getInstance(context) }
                    val liveData = remember { workManager.getWorkInfosForUniqueWorkLiveData("SyncUniqueWork") }
                    var workInfos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
                    DisposableEffect(liveData) {
                        val observer = androidx.lifecycle.Observer<List<WorkInfo>> { list ->
                            workInfos = list ?: emptyList()
                        }
                        liveData.observeForever(observer)
                        onDispose {
                            liveData.removeObserver(observer)
                        }
                    }
                    
                    val syncWorkInfo = workInfos.firstOrNull()
                    val isSyncingByFirebase = syncWorkInfo != null && (
                        syncWorkInfo.state == WorkInfo.State.RUNNING || 
                        syncWorkInfo.state == WorkInfo.State.ENQUEUED
                    )

                    var previousState by remember { mutableStateOf<WorkInfo.State?>(null) }
                    LaunchedEffect(syncWorkInfo) {
                        val state = syncWorkInfo?.state
                        if (previousState == WorkInfo.State.RUNNING) {
                            if (state == WorkInfo.State.SUCCEEDED) {
                                val msg = syncWorkInfo.outputData.getString("message") ?: "Sinkronisasi Sukses!"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else if (state == WorkInfo.State.FAILED) {
                                val msg = syncWorkInfo.outputData.getString("message") ?: "kesalahan jaringan"
                                Toast.makeText(context, "Gagal sinkronisasi: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                        previousState = state
                    }

                    PremiumButton(
                        text = if (isSyncingByFirebase) "MENYINKRONKAN..." else "SINKRONKAN TRANSAKSI SEKARANG",
                        onClick = {
                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                            
                            val syncWorkRequest = OneTimeWorkRequestBuilder<com.example.worker.SyncWorker>()
                                .setConstraints(constraints)
                                .build()
                            
                            workManager.enqueueUniqueWork(
                                "SyncUniqueWork",
                                ExistingWorkPolicy.REPLACE,
                                syncWorkRequest
                            )
                        },
                        isActive = !isSyncingByFirebase && firestoreSyncEnabled,
                        icon = Icons.Default.Sync,
                        modifier = Modifier.fillMaxWidth().testTag("sync_firestore_button")
                    )
                }
            }

            // --- Menu Cadangan Google Drive ---
            HorizontalDivider(
                color = GhostWhite.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Card dengan tampilan yang sama persis dengan card sinkronisasi
            // Firestore di atas, supaya keduanya terbaca sebagai satu pasangan.
            Card(
                modifier = Modifier.fillMaxWidth().testTag("drive_backup_card"),
                shape = RoundedCornerShape(CornerRadius.card),
                colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDriveMenu = !showDriveMenu }
                    .testTag("drive_menu_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                    Column {
                        Text(
                            text = "Cadangan Google Drive",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite
                        )
                        Text(
                            text = if (driveBackupList.isEmpty()) {
                                "Belum ada cadangan di cloud"
                            } else {
                                "${driveBackupList.size} cadangan tersimpan · terbaru " +
                                    FormatUtils.formatDriveTimestamp(
                                        driveBackupList.maxByOrNull { it.createdTime ?: "" }?.createdTime
                                    )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = if (showDriveMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showDriveMenu) "Tutup menu Drive" else "Buka menu Drive",
                    tint = GhostWhite.copy(alpha = 0.6f)
                )
            }

            // Kegagalan Drive dulu hanya disimpan di state dan tidak pernah
            // ditampilkan, jadi user tidak tahu backup/restore gagal.
            if (driveErrorDetailMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.md))
                        .background(NeonRed.copy(alpha = 0.12f))
                        .border(1.dp, NeonRed.copy(alpha = 0.35f), RoundedCornerShape(CornerRadius.md))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .testTag("drive_error_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = NeonRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = driveErrorDetailMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDriveErrorDetailMessageChange(null) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup pesan kesalahan",
                            tint = GhostWhite.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showDriveMenu) {
                Spacer(modifier = Modifier.height(16.dp))

                // Tidak ada lagi cadangan lokal: ZIP dibuat sementara di cache,
                // diunggah ke Drive, lalu dihapus dari perangkat.
                if (connectedGoogleAccount != null) {
                    PremiumButton(
                        text = if (isDriveProcessing) "MENGUNGGAH..." else "CADANGKAN KE GOOGLE DRIVE",
                        onClick = {
                            coroutineScope.launch {
                                onIsDriveProcessingChanged(true)
                                when (val backup = BackupHelper.performBackup(context)) {
                                    is BackupHelper.BackupResult.Success -> {
                                        try {
                                            when (val upload = GoogleDriveHelper.uploadBackupToDrive(context, backup.file)) {
                                                is GoogleDriveHelper.DriveResult.Success -> {
                                                    Toast.makeText(
                                                        context,
                                                        "Cadangan berhasil diunggah ke Google Drive",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    // Pesan gagal sebelumnya sudah tidak berlaku.
                                                    onDriveErrorDetailMessageChange(null)
                                                    when (val listRes = GoogleDriveHelper.listBackupsFromDrive(context)) {
                                                        is GoogleDriveHelper.DriveResult.Success ->
                                                            onDriveBackupListChanged(listRes.data)
                                                        is GoogleDriveHelper.DriveResult.Error -> Unit
                                                    }
                                                }
                                                is GoogleDriveHelper.DriveResult.Error -> {
                                                    onDriveErrorDetailMessageChange(upload.message)
                                                }
                                            }
                                        } finally {
                                            if (backup.file.exists()) backup.file.delete()
                                        }
                                    }
                                    is BackupHelper.BackupResult.Error -> {
                                        Toast.makeText(
                                            context,
                                            "Gagal membuat cadangan: ${backup.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                onIsDriveProcessingChanged(false)
                            }
                        },
                        isActive = !isDriveProcessing,
                        icon = Icons.Default.CloudUpload,
                        modifier = Modifier.fillMaxWidth().testTag("backup_to_drive_now_button")
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pencadangan otomatis hanya berguna kalau Drive terhubung.
                    // Pengaturan jam/hari tidak ditampilkan di sini; jadwal yang
                    // tersimpan dari versi sebelumnya tetap dipakai.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TranslucentForm.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pencadangan Otomatis",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GhostWhite
                            )
                            Text(
                                text = "Unggah berkala ke Google Drive",
                                style = MaterialTheme.typography.labelSmall,
                                color = GhostWhite.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isAutoBackupEnabled,
                            onCheckedChange = { enabled ->
                                onAutoBackupEnabledChanged(enabled)
                                BackupScheduler.schedulePeriodicBackup(
                                    context = context,
                                    enabled = enabled,
                                    interval = autoBackupInterval,
                                    hour = autoBackupHour,
                                    minute = autoBackupMinute,
                                    dayOfWeek = autoBackupDayOfWeek,
                                    forceUpdate = true
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SteelBlue,
                                checkedTrackColor = SteelBlue.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("auto_backup_drive_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (driveBackupList.isEmpty()) {
                    Text(
                        text = if (connectedGoogleAccount == null) {
                            "Hubungkan akun Google dulu untuk mengaktifkan cadangan cloud."
                        } else {
                            "Belum ada cadangan. Tekan tombol di atas untuk mencadangkan ke Google Drive."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = SteelBlue.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pilih cadangan untuk dipulihkan:",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.6f)
                            )
                        }

                        driveBackupList.forEach { driveFile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TranslucentForm.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = driveFile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GhostWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FormatUtils.formatDriveTimestamp(driveFile.createdTime) +
                                            " · " + FormatUtils.formatFileSize(driveFile.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GhostWhite.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onSelectedDriveBackupToRestoreChange(driveFile) },
                                        enabled = !isDriveProcessing,
                                        modifier = Modifier.testTag("restore_drive_backup_${driveFile.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Pulihkan dari Drive",
                                            tint = SteelBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { driveBackupFileToDelete = driveFile },
                                        enabled = !isDriveProcessing
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Hapus cadangan Drive",
                                            tint = NeonRed.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        }
        
        // Disconnect Account Dialog handled within realtime context now
        if (showDisconnectConfirmation) {
            Dialog(onDismissRequest = { showDisconnectConfirmation = false }) {
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
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                        ) {
                            Text(
                                text = "Putuskan Akun Google?",
                                color = GhostWhite,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Anda akan keluar dari akun Google yang terhubung dan tidak dapat melakukan sinkronisasi otomatis lagi.",
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
                                    onClick = { showDisconnectConfirmation = false },
                                    isActive = false,
                                    modifier = Modifier.weight(1f),
                                    testTag = "cancel_disconnect_button"
                                )
                                PremiumButton(
                                    text = "Putuskan",
                                    onClick = {
                                        showDisconnectConfirmation = false
                                        GoogleDriveHelper.signOut(context) {
                                            com.example.util.FirebaseSyncHelper.signOut()
                                            // Sinkronisasi wajib mati saat akun diputus.
                                            SyncManager.disableSync(context)
                                            firestoreSyncEnabled = false
                                            onConnectedGoogleAccountChanged(null)
                                            onDriveBackupListChanged(emptyList())
                                            Toast.makeText(context, "Akses Google & penyelarasan cloud terputus.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    isActive = true,
                                    modifier = Modifier.weight(1.2f),
                                    testTag = "confirm_disconnect_button"
                                )
                            }
                        }
                    }
                }
            }
        }

    } // Closing main Column


    if (driveBackupFileToDelete != null) {
        Dialog(onDismissRequest = { driveBackupFileToDelete = null }) {
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        Text(
                            text = "Hapus Cadangan Google Drive",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apakah Anda yakin ingin menghapus salinan cadangan '${driveBackupFileToDelete?.name}' dari akun Google Drive Anda? Tindakan ini bersifat permanen.",
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
                                onClick = { driveBackupFileToDelete = null },
                                isActive = false,
                                modifier = Modifier.weight(1f),
                                testTag = "cancel_delete_drive_backup"
                            )
                            PremiumButton(
                                text = "Hapus dari Cloud",
                                onClick = {
                                    val driveFile = driveBackupFileToDelete
                                    driveBackupFileToDelete = null
                                    if (driveFile != null) {
                                        coroutineScope.launch {
                                            onIsDriveProcessingChanged(true)
                                            when (val delRes = GoogleDriveHelper.deleteBackupFromDrive(context, driveFile.id)) {
                                                is GoogleDriveHelper.DriveResult.Success -> {
                                                    Toast.makeText(context, "Berhasil menghapus berkas di Google Drive.", Toast.LENGTH_SHORT).show()
                                                    // Pesan gagal sebelumnya sudah tidak berlaku.
                                                    onDriveErrorDetailMessageChange(null)
                                                    val syncList = GoogleDriveHelper.listBackupsFromDrive(context)
                                                    if (syncList is GoogleDriveHelper.DriveResult.Success) {
                                                        onDriveBackupListChanged(syncList.data)
                                                    }
                                                }
                                                is GoogleDriveHelper.DriveResult.Error -> {
                                                    onDriveErrorDetailMessageChange(delRes.message)
                                                }
                                            }
                                            onIsDriveProcessingChanged(false)
                                        }
                                    }
                                },
                                isDestructive = true,
                                modifier = Modifier.weight(1.3f),
                                testTag = "confirm_delete_drive_backup"
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialog peringatan menyalakan sinkronisasi Firestore ---
    if (showSyncWarning) {
        Dialog(onDismissRequest = { showSyncWarning = false }) {
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Nyalakan Sinkronisasi Cloud?",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Seluruh data di HP ini (transaksi, transaksi berulang, kategori, anggaran, dan pengaturan) akan DIUNGGAH PENUH ke cloud Firestore milik akun " +
                                "${connectedGoogleAccount?.email ?: "Google"} lalu disinkronkan otomatis antar perangkat.\n\n" +
                                "Catatan: isi cloud tidak dienkripsi khusus oleh aplikasi — data tersimpan dalam bentuk yang terbaca oleh layanan cloud. Jangan nyalakan kalau Anda tidak nyaman dengan hal itu.\n\n" +
                                "Data di HP tetap utuh. Mematikan kembali sinkronisasi tidak menghapus data apa pun.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { showSyncWarning = false },
                                isActive = true,
                                modifier = Modifier.weight(1f),
                                testTag = "cancel_enable_sync"
                            )
                            PremiumButton(
                                text = "PAHAM, KONFIRMASI",
                                onClick = {
                                    showSyncWarning = false
                                    firestoreSyncEnabled = true
                                    coroutineScope.launch {
                                        SyncManager.enableSync(context)
                                        val result = com.example.util.FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
                                        Toast.makeText(
                                            context,
                                            result.getOrElse { "Sinkronisasi awal gagal: ${it.localizedMessage}" },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                isActive = true,
                                modifier = Modifier.weight(1.3f),
                                testTag = "confirm_enable_sync"
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialog konfirmasi hapus data cloud ---
    if (showDeleteCloudConfirm) {
        Dialog(onDismissRequest = { if (!isDeletingCloud) showDeleteCloudConfirm = false }) {
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Hapus Data Cloud?",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Seluruh data milik akun ${connectedGoogleAccount?.email ?: "Google"} yang tersimpan di cloud Firestore akan DIHAPUS PERMANEN.\n\n" +
                                "Data di HP ini TIDAK dihapus dan tetap bisa dipakai. Sinkronisasi akan dimatikan otomatis setelah selesai. " +
                                "Kalau dinyalakan lagi nanti, seluruh isi HP akan diunggah ulang dari awal.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        if (isDeletingCloud) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = NeonRed
                                )
                                Text(
                                    text = "Menghapus data cloud...",
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { showDeleteCloudConfirm = false },
                                isActive = !isDeletingCloud,
                                modifier = Modifier.weight(1f),
                                testTag = "cancel_delete_cloud_data"
                            )
                            PremiumButton(
                                text = "PAHAM, KONFIRMASI",
                                onClick = {
                                    coroutineScope.launch {
                                        isDeletingCloud = true
                                        val report = SyncManager.deleteAllCloudData(context)
                                        isDeletingCloud = false
                                        showDeleteCloudConfirm = false
                                        firestoreSyncEnabled = false
                                        Toast.makeText(
                                            context,
                                            if (report.errors.isEmpty()) {
                                                "Data cloud dihapus. Data di HP tidak disentuh."
                                            } else {
                                                "Sebagian data cloud gagal dihapus: ${report.errors.joinToString("; ")}"
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                isActive = !isDeletingCloud,
                                isDestructive = true,
                                modifier = Modifier.weight(1.3f),
                                testTag = "confirm_delete_cloud_data"
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialog konfirmasi pemulihan dari Drive ---
    // Alur: unduh ZIP -> (best effort) cadangkan pengaman ke Drive -> validasi
    // & tukar database + kunci + pengaturan -> aplikasi mulai ulang sendiri.
    val restoreCandidate = selectedDriveBackupToRestore
    if (restoreCandidate != null) {
        Dialog(onDismissRequest = { if (!isRestoring) onSelectedDriveBackupToRestoreChange(null) }) {
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Pulihkan Cadangan Ini?",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = restoreCandidate.name,
                            color = SteelBlue,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = FormatUtils.formatDriveTimestamp(restoreCandidate.createdTime) +
                                " · " + FormatUtils.formatFileSize(restoreCandidate.sizeBytes),
                            color = GhostWhite.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Seluruh data dan pengaturan di HP ini akan DIGANTIKAN isi cadangan. " +
                                "Sebelum menimpa, aplikasi otomatis membuat cadangan pengaman ke Google Drive " +
                                "(bila akun terhubung). Kunci database ikut dipulihkan sehingga cadangan dari " +
                                "perangkat lain tetap bisa dibuka. PIN perangkat tidak ikut dipulihkan. " +
                                "Aplikasi akan dimulai ulang setelah selesai.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        if (isRestoring) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SteelBlue
                                )
                                Text(
                                    text = "Memulihkan... jangan tutup aplikasi.",
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { onSelectedDriveBackupToRestoreChange(null) },
                                isActive = !isRestoring,
                                modifier = Modifier.weight(1f),
                                testTag = "cancel_restore_drive_backup"
                            )
                            PremiumButton(
                                text = "PULIHKAN SEKARANG",
                                onClick = {
                                    coroutineScope.launch {
                                        isRestoring = true
                                        val downloadTarget = File(context.cacheDir, "restore_download.zip")
                                        when (val download = GoogleDriveHelper.downloadBackupFromDrive(context, restoreCandidate.id, downloadTarget)) {
                                            is GoogleDriveHelper.DriveResult.Error -> {
                                                isRestoring = false
                                                Toast.makeText(context, download.message, Toast.LENGTH_LONG).show()
                                                onDriveErrorDetailMessageChange(download.message)
                                            }
                                            is GoogleDriveHelper.DriveResult.Success -> {
                                                // Cadangan pengaman sebelum menimpa data saat ini.
                                                if (connectedGoogleAccount != null) {
                                                    try {
                                                        when (val safety = BackupHelper.performBackup(context)) {
                                                            is BackupHelper.BackupResult.Success -> {
                                                                try {
                                                                    GoogleDriveHelper.uploadBackupToDrive(context, safety.file)
                                                                } finally {
                                                                    if (safety.file.exists()) safety.file.delete()
                                                                }
                                                            }
                                                            is BackupHelper.BackupResult.Error -> Unit
                                                        }
                                                    } catch (e: Exception) {
                                                        com.example.util.SecureLog.e("DatabaseBackupSection", "Cadangan pengaman gagal dibuat", e)
                                                    }
                                                }
                                                val outcome = BackupHelper.restoreBackup(context, downloadTarget)
                                                if (!outcome.success) {
                                                    isRestoring = false
                                                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                                                    onDriveErrorDetailMessageChange(outcome.message)
                                                }
                                                // Sukses: aplikasi dimulai ulang oleh restoreBackup.
                                            }
                                        }
                                    }
                                },
                                isActive = !isRestoring,
                                modifier = Modifier.weight(1.3f),
                                testTag = "confirm_restore_drive_backup"
                            )
                        }
                    }
                }
            }
        }
    }
}
