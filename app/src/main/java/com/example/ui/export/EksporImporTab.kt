package com.example.ui.export

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialDialogCard
import com.example.ui.components.EditorialToggleRow
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.theme.*
import com.example.util.GoogleDriveHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Tab Simpan: ekspor/impor, sinkronisasi cloud, cadangan Drive, PIN, dan Sampah.
 *
 * Semua bagian memakai permukaan datar dengan pemisah garis rambut, sama seperti
 * tab Tambah. Tidak ada kartu bersarang: satu bagian = satu label mono + judul
 * serif + isinya.
 */
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

    val securityPrefs = remember { com.example.util.SecurePrefsHelper.getEncryptedPrefs(context, "app_security_prefs") }

    // Backup & Cloud sync states
    var isAutoBackupEnabled by remember { mutableStateOf(securityPrefs.getBoolean("auto_backup_enabled", false)) }
    var autoBackupInterval by remember { mutableStateOf(securityPrefs.getString("auto_backup_interval", "daily") ?: "daily") }
    var autoBackupHour by remember { mutableStateOf(securityPrefs.getInt("auto_backup_hour", 2)) }
    var autoBackupMinute by remember { mutableStateOf(securityPrefs.getInt("auto_backup_minute", 0)) }
    var autoBackupDayOfWeek by remember { mutableStateOf(securityPrefs.getInt("auto_backup_day_of_week", java.util.Calendar.SUNDAY)) }

    var connectedGoogleAccount by remember { mutableStateOf(GoogleDriveHelper.getSignedInAccount(context)) }
    var isDriveProcessing by remember { mutableStateOf(false) }
    var driveBackupList by remember { mutableStateOf<List<GoogleDriveHelper.DriveBackupFile>>(emptyList()) }
    var selectedDriveBackupToRestore by remember { mutableStateOf<GoogleDriveHelper.DriveBackupFile?>(null) }
    var driveErrorDetailMessage by remember { mutableStateOf<String?>(null) }

    // State PIN
    var showPinDialog by remember { mutableStateOf(false) }
    // Penjelasan PIN pindah ke ikon (i): halaman tidak lagi penuh teks.
    var showSecurityInfo by remember { mutableStateOf(false) }
    // H4a: mematikan PIN wajib verifikasi PIN lama dulu
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var pinEnabled by remember { mutableStateOf(com.example.util.PinUtils.isPinEnabled(context)) }

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
                    Toast.makeText(context, "Berhasil menghubungkan akun Google.", Toast.LENGTH_SHORT).show()
                    // Gerbang toggle ada di SyncManager.syncAll; panggilan ini tidak
                    // mengunggah apa pun selama toggle belum dinyalakan pengguna.
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

    MaterialTheme(typography = EditorialTypography) {
        // Kerangka gulir sama dengan tab Tambah: kolom luar yang menggeser,
        // kolom dalam 560 dp supaya baris teks tidak melebar di layar besar.
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideScreen) Spacing.xl else Spacing.lg)
                    .padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Eyebrow(text = "Simpan")

                Text(
                    text = "Laporan & ekspor data",
                    style = MaterialTheme.typography.displaySmall,
                    color = Ink
                )

                HairlineDivider()

                UnifiedExportCard(
                    isWideScreen = isWideScreen,
                    availableMonths = availableMonths,
                    selectedMonths = selectedMonths,
                    onSelectedMonthsChanged = { selectedMonths = it },
                    onExportPdfFile = onExportPdfFile,
                    onExportCsvFile = onExportCsvFile,
                    onImportCsvFile = onImportCsvFile
                )

                HairlineDivider()

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
                    connectedGoogleAccount = connectedGoogleAccount,
                    onConnectedGoogleAccountChanged = { connectedGoogleAccount = it },
                    isDriveProcessing = isDriveProcessing,
                    onIsDriveProcessingChanged = { isDriveProcessing = it },
                    driveBackupList = driveBackupList,
                    onDriveBackupListChanged = { driveBackupList = it },
                    selectedDriveBackupToRestore = selectedDriveBackupToRestore,
                    onSelectedDriveBackupToRestoreChange = { selectedDriveBackupToRestore = it },
                    driveErrorDetailMessage = driveErrorDetailMessage,
                    onDriveErrorDetailMessageChange = { driveErrorDetailMessage = it },
                    onConnectGoogleDrive = {
                        val client = GoogleDriveHelper.getGoogleSignInClient(context)
                        googleSignInLauncher.launch(client.signInIntent)
                    }
                )

                HairlineDivider()

                // Keamanan (PIN) di atas Sampah: pengaturan ini lebih sering dibuka
                // daripada memulihkan transaksi yang terlanjur dihapus.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Eyebrow(text = "Keamanan")
                        IconButton(
                            onClick = { showSecurityInfo = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info PIN aplikasi",
                                tint = InkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "PIN aplikasi",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = SerifFamily,
                        color = Ink
                    )
                    EditorialToggleRow(
                        title = if (pinEnabled) "PIN aktif" else "Aktifkan PIN",
                        checked = pinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                // H4a: jangan matikan langsung — minta verifikasi PIN lama
                                showDisablePinDialog = true
                            }
                        },
                        testTag = "pin_switch"
                    )
                    if (pinEnabled) {
                        EditorialButton(
                            text = "Ubah PIN",
                            onClick = { showPinDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            style = EditorialButtonStyle.Ghost,
                            trailingArrow = false
                        )
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

                if (showSecurityInfo) {
                    EditorialDialog(onDismissRequest = { showSecurityInfo = false }) {
                        EditorialDialogCard(title = "Tentang PIN aplikasi") {
                            Text(
                                text = "PIN mengunci aplikasi saat dibuka, jadi orang lain tidak bisa melihat catatan keuangan Anda.\n\n" +
                                    "PIN hanya tersimpan di perangkat ini dan tidak ikut tercadang ke Google Drive, sehingga perangkat hasil pemulihan cadangan akan meminta PIN baru.\n\n" +
                                    "Mematikan PIN meminta PIN lama dimasukkan lebih dulu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted,
                                lineHeight = 20.sp
                            )
                            EditorialButton(
                                text = "Mengerti",
                                onClick = { showSecurityInfo = false },
                                modifier = Modifier.fillMaxWidth(),
                                trailingArrow = false,
                                expanded = true
                            )
                        }
                    }
                }

                HairlineDivider()

                // Sampah: transaksi yang dihapus masih bisa dipulihkan.
                TrashSection(
                    deletedRecords = deletedRecords,
                    onRestore = onRestoreRecord,
                    onRestoreAll = onRestoreAllRecords,
                    onEmptyTrash = onEmptyTrash
                )

                Spacer(modifier = Modifier.height(Spacing.xxxl))
            }
        }
    }
}
