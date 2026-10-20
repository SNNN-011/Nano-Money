package com.example.ui.export

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.sync.SyncCursors
import com.example.data.sync.SyncManager
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialDialogCard
import com.example.ui.components.EditorialToggleRow
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.config.FormatUtils
import com.example.ui.theme.*
import com.example.util.BackupHelper
import com.example.util.BackupScheduler
import com.example.util.GoogleDriveHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sinkronisasi Firestore + cadangan Google Drive.
 *
 * Logika (unggah, unduh, pemulihan, jadwal cadangan) tidak diubah. Yang berubah
 * hanya permukaannya: dua bagian datar bergaris rambut, bukan dua kartu gelap
 * berlapis gradien.
 */
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
    onConnectGoogleDrive: () -> Unit
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
    var showFirestoreHelpInfo by remember { mutableStateOf(false) }

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
        // ── Sinkronisasi realtime ────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Eyebrow(text = "Sinkronisasi")
                IconButton(
                    onClick = { showFirestoreHelpInfo = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Panduan sinkronisasi realtime",
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "Sinkronisasi realtime",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SerifFamily,
                color = Ink
            )
            Text(
                text = "Sinkronkan catatan transaksi Anda secara otomatis ke cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )

            if (connectedGoogleAccount == null) {
                EditorialButton(
                    text = "Hubungkan akun Google",
                    onClick = onConnectGoogleDrive,
                    modifier = Modifier.fillMaxWidth(),
                    expanded = true,
                    testTag = "connect_firestore_button"
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Akun: ${connectedGoogleAccount.email ?: "Terhubung"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showDisconnectConfirmation = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Putuskan akun",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Toggle sinkronisasi: default MATI. Saat mati tidak ada data
                // lokal yang dikirim ke cloud; menyalakan butuh konfirmasi.
                EditorialToggleRow(
                    title = "Sinkronisasi Firestore",
                    subtitle = if (firestoreSyncEnabled) {
                        "Aktif — data terkirim & tersinkron ke cloud"
                    } else {
                        "Nonaktif — data tetap hanya di HP ini"
                    },
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
                    testTag = "firestore_sync_toggle"
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

                EditorialButton(
                    text = if (isSyncingByFirebase) "Menyinkronkan..." else "Sinkronkan transaksi sekarang",
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncingByFirebase && firestoreSyncEnabled,
                    expanded = true,
                    testTag = "sync_firestore_button"
                )

                // Sinkron dulu, aksi hapus data cloud di bawahnya.
                EditorialButton(
                    text = if (isDeletingCloud) "Menghapus data cloud..." else "Hapus data cloud",
                    onClick = { showDeleteCloudConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    style = EditorialButtonStyle.Ghost,
                    trailingArrow = false,
                    accent = BurntOrange,
                    enabled = !isDeletingCloud,
                    expanded = true,
                    testTag = "delete_cloud_data_button"
                )
            }
        }

        HairlineDivider()

        // ── Cadangan Google Drive ────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Eyebrow(text = "Cadangan")
            Text(
                text = "Cadangan Google Drive",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SerifFamily,
                color = Ink,
                modifier = Modifier.testTag("drive_menu_header")
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
                color = InkMuted
            )

            // Kegagalan Drive dulu hanya disimpan di state dan tidak pernah
            // ditampilkan, jadi user tidak tahu backup/restore gagal.
            if (driveErrorDetailMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.md))
                        .background(BurntOrange.copy(alpha = 0.08f))
                        .border(1.dp, BurntOrange.copy(alpha = 0.35f), RoundedCornerShape(CornerRadius.md))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .testTag("drive_error_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = BurntOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = driveErrorDetailMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDriveErrorDetailMessageChange(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup pesan kesalahan",
                            tint = InkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Tidak ada lagi cadangan lokal: ZIP dibuat sementara di cache,
            // diunggah ke Drive, lalu dihapus dari perangkat.
            if (connectedGoogleAccount != null) {
                EditorialButton(
                    text = if (isDriveProcessing) "Mengunggah..." else "Cadangkan ke Google Drive",
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDriveProcessing,
                    expanded = true,
                    testTag = "backup_to_drive_now_button"
                )

                // Pencadangan otomatis hanya berguna kalau Drive terhubung.
                // Pengaturan jam/hari tidak ditampilkan di sini; jadwal yang
                // tersimpan dari versi sebelumnya tetap dipakai.
                EditorialToggleRow(
                    title = "Pencadangan otomatis",
                    subtitle = "Unggah berkala ke Google Drive",
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
                    testTag = "auto_backup_drive_switch"
                )
            }

            if (driveBackupList.isEmpty()) {
                Text(
                    text = if (connectedGoogleAccount == null) {
                        "Hubungkan akun Google dulu untuk mengaktifkan cadangan cloud."
                    } else {
                        "Belum ada cadangan. Tekan tombol di atas untuk mencadangkan ke Google Drive."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            } else {
                Eyebrow(text = "Pilih cadangan untuk dipulihkan")

                driveBackupList.forEachIndexed { index, driveFile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = driveFile.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = FormatUtils.formatDriveTimestamp(driveFile.createdTime) +
                                    " · " + FormatUtils.formatFileSize(driveFile.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onSelectedDriveBackupToRestoreChange(driveFile) },
                            enabled = !isDriveProcessing,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("restore_drive_backup_${driveFile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Pulihkan dari Drive",
                                tint = Ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { driveBackupFileToDelete = driveFile },
                            enabled = !isDriveProcessing,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Hapus cadangan Drive",
                                tint = BurntOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (index < driveBackupList.lastIndex) HairlineDivider()
                }
            }
        }
    }

    // ── Dialog panduan sinkronisasi ──────────────────────────────────────────
    if (showFirestoreHelpInfo) {
        EditorialDialog(onDismissRequest = { showFirestoreHelpInfo = false }) {
            EditorialDialogCard(title = "Panduan sinkronisasi realtime") {
                Text(
                    text = "Sinkronkan seluruh catatan transaksi finansial Anda secara aman ke database awan Firestore. Data tersinkron otomatis antar perangkat yang terhubung ke akun yang sama.\n\nHubungkan akun Google Anda untuk mengaktifkan sinkronisasi realtime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                EditorialButton(
                    text = "Tutup",
                    onClick = { showFirestoreHelpInfo = false },
                    modifier = Modifier.fillMaxWidth(),
                    trailingArrow = false,
                    expanded = true
                )
            }
        }
    }

    // ── Dialog putuskan akun ─────────────────────────────────────────────────
    if (showDisconnectConfirmation) {
        EditorialDialog(onDismissRequest = { showDisconnectConfirmation = false }) {
            EditorialDialogCard(title = "Putuskan akun Google?") {
                Text(
                    text = "Anda akan keluar dari akun Google yang terhubung dan tidak dapat melakukan sinkronisasi otomatis lagi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { showDisconnectConfirmation = false },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        testTag = "cancel_disconnect_button"
                    )
                    EditorialButton(
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
                        modifier = Modifier.weight(1f),
                        accent = BurntOrange,
                        trailingArrow = false,
                        testTag = "confirm_disconnect_button"
                    )
                }
            }
        }
    }

    // ── Dialog hapus cadangan Drive ──────────────────────────────────────────
    if (driveBackupFileToDelete != null) {
        EditorialDialog(onDismissRequest = { driveBackupFileToDelete = null }) {
            EditorialDialogCard(title = "Hapus cadangan Google Drive") {
                Text(
                    text = "Salinan '${driveBackupFileToDelete?.name}' akan dihapus dari akun Google Drive Anda. Tindakan ini permanen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { driveBackupFileToDelete = null },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        testTag = "cancel_delete_drive_backup"
                    )
                    EditorialButton(
                        text = "Hapus dari cloud",
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
                        modifier = Modifier.weight(1.2f),
                        accent = BurntOrange,
                        trailingArrow = false,
                        testTag = "confirm_delete_drive_backup"
                    )
                }
            }
        }
    }

    // ── Dialog peringatan menyalakan sinkronisasi Firestore ──────────────────
    if (showSyncWarning) {
        EditorialDialog(onDismissRequest = { showSyncWarning = false }) {
            EditorialDialogCard(title = "Nyalakan sinkronisasi cloud?") {
                Text(
                    text = "Seluruh data di HP ini (transaksi, transaksi berulang, kategori, anggaran, dan pengaturan) akan DIUNGGAH PENUH ke cloud Firestore milik akun " +
                        "${connectedGoogleAccount?.email ?: "Google"} lalu disinkronkan otomatis antar perangkat.\n\n" +
                        "Catatan: isi cloud tidak dienkripsi khusus oleh aplikasi — data tersimpan dalam bentuk yang terbaca oleh layanan cloud. Jangan nyalakan kalau Anda tidak nyaman dengan hal itu.\n\n" +
                        "Data di HP tetap utuh. Mematikan kembali sinkronisasi tidak menghapus data apa pun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { showSyncWarning = false },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        testTag = "cancel_enable_sync"
                    )
                    EditorialButton(
                        text = "Paham, konfirmasi",
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
                        modifier = Modifier.weight(1.2f),
                        trailingArrow = false,
                        testTag = "confirm_enable_sync"
                    )
                }
            }
        }
    }

    // ── Dialog konfirmasi hapus data cloud ───────────────────────────────────
    if (showDeleteCloudConfirm) {
        EditorialDialog(onDismissRequest = { if (!isDeletingCloud) showDeleteCloudConfirm = false }) {
            EditorialDialogCard(title = "Hapus data cloud?") {
                Text(
                    text = "Seluruh data milik akun ${connectedGoogleAccount?.email ?: "Google"} yang tersimpan di cloud Firestore akan DIHAPUS PERMANEN.\n\n" +
                        "Data di HP ini TIDAK dihapus dan tetap bisa dipakai. Sinkronisasi akan dimatikan otomatis setelah selesai. " +
                        "Kalau dinyalakan lagi nanti, seluruh isi HP akan diunggah ulang dari awal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                if (isDeletingCloud) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BurntOrange
                        )
                        Text(
                            text = "Menghapus data cloud...",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { showDeleteCloudConfirm = false },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        enabled = !isDeletingCloud,
                        testTag = "cancel_delete_cloud_data"
                    )
                    EditorialButton(
                        text = "Paham, konfirmasi",
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
                        modifier = Modifier.weight(1.2f),
                        accent = BurntOrange,
                        trailingArrow = false,
                        enabled = !isDeletingCloud,
                        testTag = "confirm_delete_cloud_data"
                    )
                }
            }
        }
    }

    // ── Dialog konfirmasi pemulihan dari Drive ───────────────────────────────
    // Alur: unduh ZIP -> (best effort) cadangkan pengaman ke Drive -> validasi
    // & tukar database + kunci + pengaturan -> aplikasi mulai ulang sendiri.
    val restoreCandidate = selectedDriveBackupToRestore
    if (restoreCandidate != null) {
        EditorialDialog(onDismissRequest = { if (!isRestoring) onSelectedDriveBackupToRestoreChange(null) }) {
            EditorialDialogCard(title = "Pulihkan cadangan ini?") {
                Text(
                    text = restoreCandidate.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink
                )
                Text(
                    text = FormatUtils.formatDriveTimestamp(restoreCandidate.createdTime) +
                        " · " + FormatUtils.formatFileSize(restoreCandidate.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Text(
                    text = "Seluruh data dan pengaturan di HP ini akan DIGANTIKAN isi cadangan. " +
                        "Sebelum menimpa, aplikasi otomatis membuat cadangan pengaman ke Google Drive " +
                        "(bila akun terhubung). Kunci database ikut dipulihkan sehingga cadangan dari " +
                        "perangkat lain tetap bisa dibuka. PIN perangkat tidak ikut dipulihkan. " +
                        "Aplikasi akan dimulai ulang setelah selesai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
                if (isRestoring) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Ink
                        )
                        Text(
                            text = "Memulihkan... jangan tutup aplikasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    EditorialButton(
                        text = "Batal",
                        onClick = { onSelectedDriveBackupToRestoreChange(null) },
                        modifier = Modifier.weight(1f),
                        style = EditorialButtonStyle.Ghost,
                        trailingArrow = false,
                        enabled = !isRestoring,
                        testTag = "cancel_restore_drive_backup"
                    )
                    EditorialButton(
                        text = "Pulihkan sekarang",
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
                        modifier = Modifier.weight(1.2f),
                        trailingArrow = false,
                        enabled = !isRestoring,
                        testTag = "confirm_restore_drive_backup"
                    )
                }
            }
        }
    }
}
