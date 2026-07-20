package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import com.example.util.SecureLog
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.util.FirebaseSyncHelper

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        SecureLog.d("SyncWorker", "Memulai sinkronisasi via WorkManager... Attempt: $runAttemptCount")
        val context = applicationContext
        return try {
            val syncRes = FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
            if (syncRes.isSuccess) {
                val message = syncRes.getOrNull() ?: "Sinkronisasi Sukses!"
                SecureLog.d("SyncWorker", "Sinkronisasi sukses: $message")
                Result.success(workDataOf("message" to message))
            } else {
                val exception = syncRes.exceptionOrNull()
                SecureLog.e("SyncWorker", "Sinkronisasi gagal", exception)
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("message" to "Sinkronisasi gagal. Periksa koneksi internet Anda."))
                }
            }
        } catch (e: Exception) {
            SecureLog.e("SyncWorker", "Gagal melakukan sinkronisasi", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("message" to "Sinkronisasi gagal. Silakan coba lagi nanti."))
            }
        }
    }
}
