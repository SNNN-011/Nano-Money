package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.common.FinancialTrackerScreen
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialDialog
import com.example.ui.components.EditorialDialogCard
import com.example.ui.theme.*

// H4b: jeda minimum di background sebelum PIN dikunci lagi. Alur singkat seperti
// pemilih file (SAF) untuk ekspor/impor atau sign-in Drive tidak ikut terkunci.
private const val RELOCK_GRACE_MS = 30_000L
private const val STATE_PROCESS_TOKEN = "process_token"

// H4b: token per proses — dipakai untuk membedakan rotasi (proses sama) dari
// process death (proses baru, app dibunuh sistem) saat restore rememberSaveable.
private val PROCESS_TOKEN = java.util.UUID.randomUUID().toString()

class MainActivity : FragmentActivity() {

  // H4b: true = PIN wajib dimasukkan lagi (dipicu lifecycle/process death).
  // Dibuat state Compose supaya langsung terlihat sebelum frame pertama.
  private val requirePinNow = androidx.compose.runtime.mutableStateOf(false)
  private var stoppedAtMillis = 0L

  override fun onStart() {
    super.onStart()
    val stoppedAt = stoppedAtMillis
    stoppedAtMillis = 0L
    if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > RELOCK_GRACE_MS &&
        com.example.util.PinUtils.isPinEnabled(this)
    ) {
      requirePinNow.value = true
    }
  }

  override fun onStop() {
    super.onStop()
    stoppedAtMillis = System.currentTimeMillis()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // H4b: simpan penanda proses supaya restore bisa dibedakan rotasi vs process death
    outState.putString(STATE_PROCESS_TOKEN, PROCESS_TOKEN)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // H4b: restore setelah process death (proses baru) = PIN wajib lagi. Rotasi biasa
    // memakai proses yang sama sehingga status unlock tidak ikut terkunci.
    if (savedInstanceState?.getString(STATE_PROCESS_TOKEN)?.let { it != PROCESS_TOKEN } == true &&
        com.example.util.PinUtils.isPinEnabled(this)
    ) {
      requirePinNow.value = true
    }

    // M16: FLAG_SECURE sumber kebenarannya pref lokal user; Remote Config hanya
    // boleh MEMPERKUAT (tidak boleh mematikan bila user mengaktifkan).
    val securityPrefs = com.example.util.SecurePrefsHelper.getEncryptedPrefs(applicationContext, "app_security_prefs")
    val applySecureFlag = {
      val userEnabled = securityPrefs.getBoolean("user_prevent_screenshot", false)
      if (userEnabled || com.example.util.RemoteConfigHelper.preventScreenshot.value) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
    applySecureFlag() // sinkron saat start, sebelum frame pertama

    // Inisialisasi Firebase Remote Config
    com.example.util.RemoteConfigHelper.initAndFetchConfig()

    // Inisialisasi Firebase Telemetry (Crashlytics dan Analytics)
    com.example.util.TelemetryHelper.initialize(applicationContext)

    // Remote Config hanya boleh menambah proteksi — hitung ulang saat nilainya berubah
    lifecycleScope.launch {
      com.example.util.RemoteConfigHelper.preventScreenshot.collect { applySecureFlag() }
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val context = androidx.compose.ui.platform.LocalContext.current

          // L3: cek root/emulator berat (exec, scan file) dijalankan di Dispatchers.IO,
          // bukan main thread. Status tidak memblokir app — hanya peringatan (fail-open terkendali).
          var securityStatus by remember { mutableStateOf<com.example.util.SecurityUtil.SecurityStatus?>(null) }
          var securityWarningDismissed by rememberSaveable { mutableStateOf(false) }

          LaunchedEffect(Unit) {
            val status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              com.example.util.SecurityUtil.checkSecurityStatus(context)
            }
            securityStatus = status
            com.example.util.TelemetryHelper.trackSecurityCheck(
              status = status.name,
              isSafe = status == com.example.util.SecurityUtil.SecurityStatus.SAFE
            )
          }

          LaunchedEffect(Unit) {
            val isAutoBackup = securityPrefs.getBoolean("auto_backup_enabled", false)
            val autoBackupInterval = securityPrefs.getString("auto_backup_interval", "daily") ?: "daily"
            val autoBackupHour = securityPrefs.getInt("auto_backup_hour", 2)
            val autoBackupMinute = securityPrefs.getInt("auto_backup_minute", 0)
            val autoBackupDayOfWeek = securityPrefs.getInt("auto_backup_day_of_week", java.util.Calendar.SUNDAY)
            com.example.util.BackupScheduler.schedulePeriodicBackup(
                context, isAutoBackup, autoBackupInterval, autoBackupHour, autoBackupMinute, autoBackupDayOfWeek, forceUpdate = false
            )

            com.example.util.NotificationScheduler.scheduleDailyReminder(
                context, true, 20, 0
            )

            // Hasil restore backup harus menang atas versi cloud: tandai PENDING dulu.
            com.example.util.RestoreSyncMarker.consumeIfPresent(context)

            // Sinkronisasi hanya berjalan kalau pengguna menyalakan toggle-nya
            // (default MATI): tanpa ini tidak ada data yang menyentuh cloud.
            if (com.example.util.FirebaseSyncHelper.isUserSignedIn() &&
                com.example.data.sync.SyncCursors.isSyncEnabled(context)
            ) {
                // Realtime: perubahan dari perangkat lain langsung masuk.
                com.example.data.sync.SyncManager.startRealtime(context)
                launch {
                    com.example.util.FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
                }
            }
        }

          // M8a: state sesi harus selamat rotasi/process death
          var isLaunching by rememberSaveable { mutableStateOf(true) }
          // null = belum dicek, true = sudah unlock/PIN tidak aktif, false = perlu PIN
          var pinUnlocked by rememberSaveable { mutableStateOf<Boolean?>(null) }

          val permissionLauncher = rememberLauncherForActivityResult(
              contract = ActivityResultContracts.RequestMultiplePermissions()
          ) { _ ->
              securityPrefs.edit().putBoolean("permission_dialog_shown", true).apply()
          }

          // Cek PIN segera di background, parallel dengan splash
          LaunchedEffect(Unit) {
              if (pinUnlocked == null) {
                  val needsPin = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                      com.example.util.PinUtils.isPinEnabled(context)
                  }
                  pinUnlocked = !needsPin
              }
              // Permission check — setelah PIN dicek
              if (!securityPrefs.getBoolean("permission_dialog_shown", false)) {
                  val permissions = mutableListOf<String>()
                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                      permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                  }
                  permissions.add(android.Manifest.permission.CAMERA)
                  permissionLauncher.launch(permissions.toTypedArray())
                  securityPrefs.edit().putBoolean("permission_dialog_shown", true).apply()
              }
          }

          // H4b: lifecycle (onStart) menandai app lama di background → PIN wajib lagi.
          // Hanya menyentuh flag unlock; alur verifikasi/ganti/lupa PIN tidak diubah.
          when {
            isLaunching || pinUnlocked == null -> {
                  com.example.ui.common.StartupScreen(
                      visible = true,
                      onFinished = { isLaunching = false }
                  )
              }
              pinUnlocked == false || requirePinNow.value -> {
                  com.example.ui.common.PinUnlockScreen(
                      onUnlocked = {
                          pinUnlocked = true
                          requirePinNow.value = false
                      },
                      onExit = { finish() }
                  )
              }
              else -> {
                  FinancialTrackerScreen(application = application, showStartupSplash = false)
              }
          }

          // L3: peringatan root/emulator — tanpa blokir total, tanpa exitProcess;
          // app tetap jalan (fail-open terkendali) setelah user menutup dialog.
          val warningStatus = securityStatus
          if (warningStatus != null &&
              warningStatus != com.example.util.SecurityUtil.SecurityStatus.SAFE &&
              !securityWarningDismissed
          ) {
            SecurityWarningDialog(warningStatus) { securityWarningDismissed = true }
          }
         }
        }
      }
    }
}

// L3: pengganti layar blokir — hanya peringatan, user boleh lanjut.
@Composable
private fun SecurityWarningDialog(
  status: com.example.util.SecurityUtil.SecurityStatus,
  onContinue: () -> Unit
) {
  val isRooted = status == com.example.util.SecurityUtil.SecurityStatus.ROOTED
  // Peringatan ini muncul sebelum layar utama, tapi tetap ikut permukaan kaca
  // yang sama supaya tidak ada dialog gelap bawaan Material di aplikasi.
  EditorialDialog(onDismissRequest = onContinue) {
    EditorialDialogCard(
      title = if (isRooted) "Perangkat Root Terdeteksi" else "Lingkungan Emulator Terdeteksi"
    ) {
      Text(
        text = if (isRooted) {
          "Aplikasi tetap bisa dipakai, tetapi perangkat root menurunkan jaminan keamanan data keuangan Anda. Hindari fitur sensitif di perangkat ini."
        } else {
          "Aplikasi tetap bisa dipakai di emulator, tetapi data di sini tidak terlindungi seperti di perangkat asli."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = InkMuted
      )
      EditorialButton(
        text = "Lanjutkan",
        onClick = onContinue,
        trailingArrow = false,
        expanded = true
      )
    }
  }
}
