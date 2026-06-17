package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.util.BackupHelper
import com.example.util.GoogleDriveHelper
import java.io.File

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BackupWorker", "Memulai pencadangan otomatis via WorkManager... Attempt: $runAttemptCount")
        val context = applicationContext
        val securityPrefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var shouldRetry = false
        try {
            when (val result = BackupHelper.performBackup(context, isAuto = true)) {
                is BackupHelper.BackupResult.Success -> {
                    Log.d("BackupWorker", "Pencadangan otomatis sukses: ${result.fileName}")
                    securityPrefs.edit()
                        .putString("last_auto_backup_local_status", "Sukses (${result.fileName})")
                        .putLong("last_auto_backup_local_time", now)
                        .apply()
                    
                    val isDriveBackupEnabled = securityPrefs.getBoolean("drive_backup_enabled", true)
                    var driveStatus = "Tidak Aktif"
                    // Silently upload to Google Drive if account is connected and backup is enabled
                    if (isDriveBackupEnabled && GoogleDriveHelper.getSignedInAccount(context) != null) {
                        try {
                            val backupDir = BackupHelper.getBackupDirectory(context)
                            val backupFile = File(backupDir, result.fileName)
                            when (val uploadRes = GoogleDriveHelper.uploadBackupToDrive(context, backupFile)) {
                                is GoogleDriveHelper.DriveResult.Success -> {
                                    Log.d("BackupWorker", "Unggah otomatis cadangan ke Google Drive sukses: fileId=${uploadRes.data}")
                                    securityPrefs.edit()
                                        .putString("last_auto_backup_drive_status", "Sukses")
                                        .putLong("last_auto_backup_drive_time", now)
                                        .apply()
                                    driveStatus = "Berhasil"
                                }
                                is GoogleDriveHelper.DriveResult.Error -> {
                                    Log.e("BackupWorker", "Gagal mengunggah otomatis cadangan ke Google Drive: ${uploadRes.message}")
                                    securityPrefs.edit()
                                        .putString("last_auto_backup_drive_status", "Gagal: ${uploadRes.message}")
                                        .putLong("last_auto_backup_drive_time", now)
                                        .apply()
                                    driveStatus = "Gagal (${uploadRes.message})"
                                    if (runAttemptCount < 3) {
                                        shouldRetry = true
                                    }
                                }
                            }
                        } catch (uploadException: Exception) {
                            Log.e("BackupWorker", "Terganggu saat mencoba mengunggah otomatis ke Google Drive", uploadException)
                            securityPrefs.edit()
                                .putString("last_auto_backup_drive_status", "Gagal: ${uploadException.localizedMessage}")
                                .putLong("last_auto_backup_drive_time", now)
                                .apply()
                            driveStatus = "Gagal (${uploadException.localizedMessage})"
                            if (runAttemptCount < 3) {
                                shouldRetry = true
                            }
                        }
                    } else {
                        if (!isDriveBackupEnabled) {
                            securityPrefs.edit()
                                .putString("last_auto_backup_drive_status", "Dinonaktifkan oleh pengguna")
                                .putLong("last_auto_backup_drive_time", now)
                                .apply()
                            driveStatus = "Dinonaktifkan"
                        } else {
                            securityPrefs.edit()
                                .putString("last_auto_backup_drive_status", "Tidak Aktif (Google Drive belum terhubung)")
                                .putLong("last_auto_backup_drive_time", 0L)
                                .apply()
                            driveStatus = "Tidak Aktif (Belum Terhubung)"
                        }
                    }
                    showSuccessNotification(context, result.fileName, driveStatus)
                }
                is BackupHelper.BackupResult.Error -> {
                    Log.e("BackupWorker", "Pencadangan otomatis gagal: ${result.message}")
                    securityPrefs.edit()
                        .putString("last_auto_backup_local_status", "Gagal: ${result.message}")
                        .putLong("last_auto_backup_local_time", now)
                        .apply()
                    showFailureNotification(context, result.message)
                    if (runAttemptCount < 3) {
                        shouldRetry = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Gagal melakukan pencadangan otomatis: ${e.message}")
            securityPrefs.edit()
                .putString("last_auto_backup_local_status", "Gagal: ${e.message}")
                .putLong("last_auto_backup_local_time", now)
                .apply()
            showFailureNotification(context, e.localizedMessage ?: e.toString())
            if (runAttemptCount < 3) {
                shouldRetry = true
            }
        }
        return if (shouldRetry) {
            Log.d("BackupWorker", "Menjadwalkan ulang pencadangan otomatis via WorkManager retry")
            Result.retry()
        } else {
            Result.success()
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

    private fun showSuccessNotification(context: Context, fileName: String, driveStatus: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "backup_success_notifications"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pencadangan Otomatis Berhasil",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Memberitahu jika pencadangan otomatis telah berhasil dilakukan."
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
        val pendingIntent = PendingIntent.getActivity(context, 1001, clickIntent, pendingIntentFlags)
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pencadangan Otomatis Berhasil ✅")
            .setContentText("Database berhasil dicadangkan: $fileName. Google Drive: $driveStatus")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Pencadangan otomatis berhasil diselesaikan.\n\n• File lokal: $fileName\n• Google Drive: $driveStatus"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(1001, notification)
    }
}
