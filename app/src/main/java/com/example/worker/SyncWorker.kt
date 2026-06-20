package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.util.FirebaseSyncHelper

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Memulai sinkronisasi via WorkManager... Attempt: $runAttemptCount")
        val context = applicationContext
        return try {
            val syncRes = FirebaseSyncHelper.syncFinancialRecordsWithFirestore(context)
            if (syncRes.isSuccess) {
                val message = syncRes.getOrNull() ?: "Sinkronisasi Sukses!"
                Log.d("SyncWorker", "Sinkronisasi sukses: $message")
                Result.success(workDataOf("message" to message))
            } else {
                val exception = syncRes.exceptionOrNull()
                val errorMessage = exception?.localizedMessage ?: "kesalahan jaringan"
                Log.e("SyncWorker", "Sinkronisasi gagal: $errorMessage")
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("message" to errorMessage))
                }
            }
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.toString()
            Log.e("SyncWorker", "Gagal melakukan sinkronisasi: $errorMessage", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("message" to errorMessage))
            }
        }
    }
}
