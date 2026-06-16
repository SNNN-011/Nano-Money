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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

            val prefix = if (isAuto) "auto_DataNanoMoney_" else "manual_DataNanoMoney_"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "$prefix$timestamp.db")

            // Pack both database file and shared preferences XMLs into a single ZIP-formatted archive
            ZipOutputStream(backupFile.outputStream()).use { zos ->
                // A. Add database file entry
                zos.putNextEntry(ZipEntry("database/financial_tracker_database"))
                dbFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()

                // B. Add all available shared preferences XML entries
                val dataDir = context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                Log.d("BackupHelper", "Memulai penyalinan Shared Preferences dari: ${sharedPrefsDir.absolutePath}")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles { _, name -> name.endsWith(".xml") }?.forEach { xmlFile ->
                        Log.d("BackupHelper", "Mencadangkan file preferensi: ${xmlFile.name}")
                        zos.putNextEntry(ZipEntry("shared_prefs/${xmlFile.name}"))
                        xmlFile.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                } else {
                    Log.w("BackupHelper", "Folder shared_prefs tidak ditemukan atau bukan direktori: ${sharedPrefsDir.absolutePath}")
                }
            }

            if (isAuto) {
                cleanOldAutoBackups(context)
            }

            Log.d("BackupHelper", "Settings and database successfully backed up to ${backupFile.absolutePath}")
            BackupResult.Success(backupFile.name)
        } catch (e: Exception) {
            Log.e("BackupHelper", "Error during backup: ${e.message}", e)
            BackupResult.Error(e.localizedMessage ?: e.toString())
        }
    }

    fun getBackups(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file -> 
            file.name.endsWith(".db") && (
                file.name.startsWith("auto_backup_") || 
                file.name.startsWith("manual_backup_") ||
                file.name.startsWith("auto_DataNanoMoney_") ||
                file.name.startsWith("manual_DataNanoMoney_")
            )
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    private fun cleanOldAutoBackups(context: Context) {
        try {
            val backupDir = getBackupDirectory(context)
            val autoBackups = backupDir.listFiles { file -> 
                (file.name.startsWith("auto_backup_") || file.name.startsWith("auto_DataNanoMoney_")) && file.name.endsWith(".db")
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

            // 3. Extract from ZIP or copy database file directly if it's legacy non-zip format
            if (isZipFile(backupFile)) {
                ZipInputStream(backupFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            if (entry.name == "database/financial_tracker_database") {
                                dbFile.parentFile?.mkdirs()
                                dbFile.outputStream().use { output ->
                                    zis.copyTo(output)
                                }
                            } else if (entry.name.startsWith("shared_prefs/")) {
                                val fileName = entry.name.substringAfter("shared_prefs/")
                                val dataDir = context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir
                                val sharedPrefsDir = File(dataDir, "shared_prefs")
                                if (!sharedPrefsDir.exists()) {
                                    sharedPrefsDir.mkdirs()
                                }
                                val targetPrefFile = File(sharedPrefsDir, fileName)
                                targetPrefFile.outputStream().use { output ->
                                    zis.copyTo(output)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                Log.d("BackupHelper", "Database and settings successfully restored from ZIP backup ${backupFile.name}")
            } else {
                // Backwards compatible legacy restore: copy file directly
                backupFile.inputStream().use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("BackupHelper", "Database successfully restored from legacy backup ${backupFile.name}")
            }

            // 4. Restart the screen activity or the complete application to load restored data smoothly
            restartApplication(context)
            true
        } catch (e: Exception) {
            Log.e("BackupHelper", "Error during restore: ${e.message}", e)
            false
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                read == 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
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
