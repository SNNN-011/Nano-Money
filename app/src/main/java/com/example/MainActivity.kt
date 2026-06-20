package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
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
import com.example.ui.FinancialTrackerScreen
import com.example.ui.LockScreenOverlay
import com.example.ui.theme.*

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Inisialisasi Firebase Remote Config
    com.example.util.RemoteConfigHelper.initAndFetchConfig()
    
    // Inisialisasi Firebase Telemetry (Crashlytics dan Analytics)
    com.example.util.TelemetryHelper.initialize(applicationContext)
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val context = androidx.compose.ui.platform.LocalContext.current
          val securityStatus = remember { com.example.util.SecurityUtil.checkSecurityStatus(context) }
          
          LaunchedEffect(securityStatus) {
            com.example.util.TelemetryHelper.trackSecurityCheck(
              status = securityStatus.name,
              isSafe = securityStatus == com.example.util.SecurityUtil.SecurityStatus.SAFE
            )
          }
          
          if (securityStatus != com.example.util.SecurityUtil.SecurityStatus.SAFE) {
            com.example.ui.SecurityViolationScreen(securityStatus = securityStatus)
          } else {
            val securityPrefs = remember { context.getSharedPreferences("app_security_prefs", android.content.Context.MODE_PRIVATE) }
            
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
          }
          
          val isPinEnabled = remember { securityPrefs.getBoolean("pin_enabled", false) }
          val savedPin = remember { securityPrefs.getString("saved_pin", "") ?: "" }
          val isBiometricEnabled = remember { securityPrefs.getBoolean("biometric_enabled", false) }
          
          val needsLock = isPinEnabled || isBiometricEnabled
          var isAppUnlocked by remember { mutableStateOf(!needsLock) }
          var isLaunching by remember { mutableStateOf(true) }
          
          val permissionLauncher = rememberLauncherForActivityResult(
              contract = ActivityResultContracts.RequestMultiplePermissions()
          ) { _ ->
              securityPrefs.edit().putBoolean("permission_dialog_shown", true).apply()
          }

          LaunchedEffect(isAppUnlocked, isLaunching) {
              if (isAppUnlocked && !isLaunching) {
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
          }
          
          if (isLaunching) {
            com.example.ui.StartupScreen(
                visible = true,
                onFinished = { isLaunching = false }
            )
          } else if (!isAppUnlocked) {
            LockScreenOverlay(
              context = context,
              savedPin = savedPin,
              isPinEnabled = isPinEnabled,
              isBiometricEnabled = isBiometricEnabled,
              onUnlock = { isAppUnlocked = true },
              onTriggerBiometrics = {
                showBiometricVerification {
                  isAppUnlocked = true
                }
              }
            )
          } else {
            FinancialTrackerScreen(application = application, showStartupSplash = false)
          }
         }
        }
      }
    }
  }

  private fun showBiometricVerification(onSuccess: () -> Unit) {
    val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
    val biometricPrompt = androidx.biometric.BiometricPrompt(
        this,
        executor,
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        }
    )

    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle("Kunci Aplikasi Financial")
        .setSubtitle("Gunakan metode biometrik sensor Anda")
        .setNegativeButtonText("Gunakan Kunci PIN")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        e.printStackTrace()
    }
  }
}

