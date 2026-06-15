package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.BackupWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupScheduler {
    private const val UNIQUE_WORK_NAME = "AutoBackupWork"

    fun schedulePeriodicBackup(context: Context, enabled: Boolean, interval: String, hour: Int = 2, minute: Int = 0, dayOfWeek: Int = Calendar.SUNDAY) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d("BackupScheduler", "Pencadangan otomatis dinonaktifkan")
            return
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (interval == "weekly") {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                // Jika jam atau hari tersebut sudah terlewat, jadwalkan untuk minggu esok
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            } else {
                // Jika jam tersebut sudah terlewat hari ini, jadwalkan untuk hari esok
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        val initialDelay = calendar.timeInMillis - System.currentTimeMillis()

        val repeatInterval = if (interval == "weekly") 7L else 1L

        val periodicWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        try {
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWorkRequest
            )
            Log.d(
                "BackupScheduler", 
                "Pencadangan otomatis diatur menggunakan WorkManager: $interval mulai pukul ${String.format("%02d:%02d", hour, minute)} (Initial delay: ${TimeUnit.MILLISECONDS.toMinutes(initialDelay)} menit)"
            )
        } catch (e: Exception) {
            Log.e("BackupScheduler", "Gagal mengatur WorkManager untuk pencadangan otomatis: ${e.message}", e)
        }
    }
}
