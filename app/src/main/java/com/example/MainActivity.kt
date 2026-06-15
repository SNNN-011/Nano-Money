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
              val autoBackupDayOfWeek = securityPrefs.getInt("auto_backup_day_of_week", java.util.Calendar.SUNDAY)
              com.example.util.BackupScheduler.schedulePeriodicBackup(
                  context, isAutoBackup, autoBackupInterval, autoBackupHour, autoBackupMinute, autoBackupDayOfWeek
              )

              val isReminderEnabled = securityPrefs.getBoolean("reminder_enabled", false)
              val reminderHour = securityPrefs.getInt("reminder_hour", 20)
              val reminderMinute = securityPrefs.getInt("reminder_minute", 0)
              com.example.util.NotificationScheduler.scheduleDailyReminder(
                  context, isReminderEnabled, reminderHour, reminderMinute
              )
          }
          
          val isPinEnabled = remember { securityPrefs.getBoolean("pin_enabled", false) }
          val savedPin = remember { securityPrefs.getString("saved_pin", "") ?: "" }
          val isBiometricEnabled = remember { securityPrefs.getBoolean("biometric_enabled", false) }
          
          val needsLock = isPinEnabled || isBiometricEnabled
          var isAppUnlocked by remember { mutableStateOf(!needsLock) }
          var showPermissionDialog by remember { mutableStateOf(!securityPrefs.getBoolean("permission_dialog_shown", false)) }
          var isLaunching by remember { mutableStateOf(true) }
          
          val permissionLauncher = rememberLauncherForActivityResult(
              contract = ActivityResultContracts.RequestMultiplePermissions()
          ) { _ ->
              securityPrefs.edit().putBoolean("permission_dialog_shown", true).apply()
              showPermissionDialog = false
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
            if (showPermissionDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightAbyss)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.9f)),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(GhostWhite.copy(alpha = 0.2f), GhostWhite.copy(alpha = 0.02f))
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Header Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(SteelBlue.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SteelBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Header Text
                            Text(
                                text = "Izin Akses Aplikasi",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = GhostWhite
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Demi kelancaran pelacakan keuangan, pencadangan otomatis harian, dan notifikasi pengingat tepat waktu, aplikasi memerlukan beberapa izin akses operasional:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    lineHeight = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Benefit Row 1: Notifications
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TranslucentGlass, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(SteelBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = SteelBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notifikasi & Pengingat",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GhostWhite)
                                    )
                                    Text(
                                        text = "Mengingatkan Anda mencatat pemasukan & pengeluaran di jam yang Anda tentukan agar keuangan tetap terkontrol.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = GhostWhite.copy(alpha = 0.5f), lineHeight = 16.sp)
                                    )
                                }
                            }

                            // Benefit Row 2: Camera
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TranslucentGlass, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(SteelBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = SteelBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Akses Kamera (Scan)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GhostWhite)
                                    )
                                    Text(
                                        text = "Memudahkan pengunggahan atau pemindaian foto kuitansi/nota secara instan melalui asisten obrolan AI.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = GhostWhite.copy(alpha = 0.5f), lineHeight = 16.sp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Grant Button with Beautiful Gradient
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            val permissions = mutableListOf<String>()
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissions.add(android.Manifest.permission.CAMERA)
                                            permissionLauncher.launch(permissions.toTypedArray())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Izinkan Akses",
                                        color = MidnightAbyss,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // Skip button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            securityPrefs.edit().putBoolean("permission_dialog_shown", true).apply()
                                            showPermissionDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nanti Saja",
                                        color = GhostWhite.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
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

