package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.receiver.BackupReceiver
import java.util.Calendar

object BackupScheduler {
    fun schedulePeriodicBackup(context: Context, enabled: Boolean, interval: String, hour: Int = 2, minute: Int = 0) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BackupReceiver::class.java)
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, pendingIntentFlags)
        
        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            Log.d("BackupScheduler", "Pencadangan otomatis dinonaktifkan")
            return
        }
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            // Schedule database backup for custom hour and minute
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the time has already passed today, set it to start tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val repeatInterval = if (interval == "weekly") {
            AlarmManager.INTERVAL_DAY * 7
        } else {
            // Default to daily
            AlarmManager.INTERVAL_DAY
        }
        
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                repeatInterval,
                pendingIntent
            )
            Log.d("BackupScheduler", "Pencadangan otomatis diatur: $interval mulai pukul ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            Log.e("BackupScheduler", "Gagal mengatur alarm pencadangan: ${e.message}", e)
        }
    }
}
