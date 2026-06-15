package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.FinancialTrackerScreen
import com.example.ui.LockScreenOverlay
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val context = androidx.compose.ui.platform.LocalContext.current
          val securityPrefs = remember { context.getSharedPreferences("app_security_prefs", android.content.Context.MODE_PRIVATE) }
          
          LaunchedEffect(Unit) {
              val isAutoBackup = securityPrefs.getBoolean("auto_backup_enabled", false)
              val autoBackupInterval = securityPrefs.getString("auto_backup_interval", "daily") ?: "daily"
              val autoBackupHour = securityPrefs.getInt("auto_backup_hour", 2)
              val autoBackupMinute = securityPrefs.getInt("auto_backup_minute", 0)
              com.example.util.BackupScheduler.schedulePeriodicBackup(
                  context, isAutoBackup, autoBackupInterval, autoBackupHour, autoBackupMinute
              )
          }
          
          val isPinEnabled = remember { securityPrefs.getBoolean("pin_enabled", false) }
          val savedPin = remember { securityPrefs.getString("saved_pin", "") ?: "" }
          val isBiometricEnabled = remember { securityPrefs.getBoolean("biometric_enabled", false) }
          
          val needsLock = isPinEnabled || isBiometricEnabled
          var isAppUnlocked by remember { mutableStateOf(!needsLock) }
          
          if (!isAppUnlocked) {
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
            FinancialTrackerScreen(application = application)
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

