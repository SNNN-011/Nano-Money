package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.BackupHelper
import com.example.util.GoogleDriveHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BackupReceiver", "Memulai pencadangan otomatis...")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val result = BackupHelper.performBackup(context, isAuto = true)) {
                    is BackupHelper.BackupResult.Success -> {
                        Log.d("BackupReceiver", "Pencadangan otomatis sukses: ${result.fileName}")
                        
                        // Silently upload to Google Drive if account is connected
                        if (GoogleDriveHelper.getSignedInAccount(context) != null) {
                            try {
                                val backupDir = BackupHelper.getBackupDirectory(context)
                                val backupFile = File(backupDir, result.fileName)
                                when (val uploadRes = GoogleDriveHelper.uploadBackupToDrive(context, backupFile)) {
                                    is GoogleDriveHelper.DriveResult.Success -> {
                                        Log.d("BackupReceiver", "Unggah otomatis cadangan ke Google Drive sukses: fileId=${uploadRes.data}")
                                    }
                                    is GoogleDriveHelper.DriveResult.Error -> {
                                        Log.e("BackupReceiver", "Gagal mengunggah otomatis cadangan ke Google Drive: ${uploadRes.message}")
                                    }
                                }
                            } catch (uploadException: Exception) {
                                Log.e("BackupReceiver", "Terganggu saat mencoba mengunggah otomatis ke Google Drive", uploadException)
                            }
                        }
                    }
                    is BackupHelper.BackupResult.Error -> {
                        Log.e("BackupReceiver", "Pencadangan otomatis gagal: ${result.message}")
                        showFailureNotification(context, result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupReceiver", "Gagal melakukan pencadangan otomatis: ${e.message}")
                showFailureNotification(context, e.localizedMessage ?: e.toString())
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showFailureNotification(context: Context, errorMessage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "backup_failure_notifications"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kegagalan Pencadangan Otomatis",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Memberitahu jika pencadangan otomatis database lokal gagal dilakukan."
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 1002, clickIntent, pendingIntentFlags)
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Pencadangan Otomatis Gagal ⚠️")
            .setContentText("Gagal mencadangkan database: $errorMessage. Ketuk untuk membuka aplikasi dan mencadangkan secara manual.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Gagal mencadangkan database: $errorMessage. Silakan ketuk untuk membuka aplikasi dan mencoba melakukan pencadangan secara manual agar data Anda tetap aman."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(1002, notification)
    }
}
