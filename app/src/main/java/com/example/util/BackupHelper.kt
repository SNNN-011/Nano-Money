package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {
    private const val MAX_AUTO_BACKUPS = 5

    fun getBackupDirectory(context: Context): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    sealed interface BackupResult {
        data class Success(val fileName: String) : BackupResult
        data class Error(val message: String) : BackupResult
    }

    suspend fun performBackup(context: Context, isAuto: Boolean = true): BackupResult = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            
            // 1. Check if database is currently locked/in an active transaction
            val inTransaction = try {
                db.openHelper.writableDatabase.inTransaction()
            } catch (e: Exception) {
                false
            }
            if (inTransaction) {
                return@withContext BackupResult.Error("Database sedang dalam transaksi aktif oleh proses lain. Mohon tunggu sejenak.")
            }

            // Verify database can execute read/write commands safely and is not locked
            try {
                db.openHelper.writableDatabase.query("SELECT 1", emptyArray()).use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getInt(0)
                    }
                }
            } catch (e: Exception) {
                return@withContext BackupResult.Error("Database terkunci atau tidak dapat diakses untuk pencadangan: ${e.localizedMessage}")
            }

            // 2. Check if database file exists
            val dbFile = context.getDatabasePath("financial_tracker_database")
            if (!dbFile.exists()) {
                Log.e("BackupHelper", "Database file does not exist!")
                return@withContext BackupResult.Error("Database file belum terbentuk! Harap isi minimal satu catatan keuangan terlebih dahulu.")
            }

            // 3. Prevent failure due to insufficient storage space
            val backupDir = getBackupDirectory(context)
            val dbSize = dbFile.length()
            val freeSpace = backupDir.freeSpace // Free space in bytes
            // Define minimum buffer of database size plus 5 MB of extra breathing room
            val requiredSpace = dbSize + (5 * 1024 * 1024) 
            if (freeSpace < requiredSpace) {
                val dbSizeMb = dbSize.toDouble() / (1024 * 1024)
                val freeSpaceMb = freeSpace.toDouble() / (1024 * 1024)
                return@withContext BackupResult.Error(
                    String.format(
                        Locale.getDefault(),
                        "Penyimpanan hampir penuh! Membutuhkan %.2f MB, tetapi hanya tersedia %.2f MB.",
                        dbSizeMb + 5.0,
                        freeSpaceMb
                    )
                )
            }

            // 4. Force WAL checkpoint to dump current journals into .db file safely
            try {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (e: Exception) {
                Log.w("BackupHelper", "Failed on PRAGMA wal_checkpoint, continuing backup: ${e.message}")
            }

            val prefix = if (isAuto) "auto_backup_" else "manual_backup_"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "$prefix$timestamp.db")

            dbFile.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (isAuto) {
                cleanOldAutoBackups(context)
            }

            Log.d("BackupHelper", "Database successfully backed up to ${backupFile.absolutePath}")
            BackupResult.Success(backupFile.name)
        } catch (e: Exception) {
            Log.e("BackupHelper", "Error during backup: ${e.message}", e)
            BackupResult.Error(e.localizedMessage ?: e.toString())
        }
    }

    fun getBackups(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file -> 
            file.name.endsWith(".db") && (file.name.startsWith("auto_backup_") || file.name.startsWith("manual_backup_"))
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    private fun cleanOldAutoBackups(context: Context) {
        try {
            val backupDir = getBackupDirectory(context)
            val autoBackups = backupDir.listFiles { file -> 
                file.name.startsWith("auto_backup_") && file.name.endsWith(".db")
            }?.sortedBy { it.lastModified() } ?: return

            if (autoBackups.size > MAX_AUTO_BACKUPS) {
                val toDeleteCount = autoBackups.size - MAX_AUTO_BACKUPS
                for (i in 0 until toDeleteCount) {
                    autoBackups[i].delete()
                    Log.d("BackupHelper", "Deleted old auto backup: ${autoBackups[i].name}")
                }
            }
        } catch (e: Exception) {
            Log.e("BackupHelper", "Error cleaning old backups: ${e.message}")
        }
    }

    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            // 1. Reset singleton instance & close connections
            AppDatabase.resetDatabaseInstance()

            val dbFile = context.getDatabasePath("financial_tracker_database")
            
            // 2. Clear out any WAL or SHM files so SQLite doesn't replay them and corrupt our restored database
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. Copy backup file over to the real database file
            backupFile.inputStream().use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("BackupHelper", "Database successfully restored from ${backupFile.name}")
            
            // 4. Restart the screen activity or the complete application to load restored data smoothly
            restartApplication(context)
            true
        } catch (e: Exception) {
            Log.e("BackupHelper", "Error during restore: ${e.message}", e)
            false
        }
    }

    private fun restartApplication(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            Process.killProcess(Process.myPid())
        }
    }
}
