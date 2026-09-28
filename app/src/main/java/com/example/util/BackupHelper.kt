package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.util.SecureLog
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
                TelemetryHelper.trackBackupAction("backup", false, "Database is in active transaction")
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
                TelemetryHelper.trackBackupAction("backup", false, "Database access failed: ${e.message}")
                return@withContext BackupResult.Error("Database terkunci atau tidak dapat diakses untuk pencadangan: ${e.localizedMessage}")
            }

            // 2. Check if database file exists
            val dbFile = context.getDatabasePath("financial_tracker_database")
            if (!dbFile.exists()) {
                SecureLog.e("BackupHelper", "Database file does not exist!")
                TelemetryHelper.trackBackupAction("backup", false, "Database file does not exist")
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
                TelemetryHelper.trackBackupAction("backup", false, "Insufficient storage space: db=${dbSizeMb}MB, free=${freeSpaceMb}MB")
                return@withContext BackupResult.Error(
                    String.format(
                        Locale.getDefault(),
                        "Penyimpanan hampir penuh! Membutuhkan %.2f MB, tetapi hanya tersedia %.2f MB.",
                        dbSizeMb + 5.0,
                        freeSpaceMb
                    )
                )
            }

            // 4. Buat salinan konsisten dengan VACUUM INTO.
            // Database memakai journal WAL, jadi transaksi terbaru bisa masih ada di
            // file -wal dan TIDAK ikut kalau file .db disalin apa adanya.
            // PRAGMA wal_checkpoint tidak cukup: ia gagal diam-diam kalau ada
            // pembaca aktif, sehingga backup terlihat benar padahal kurang data.
            // VACUUM INTO menulis snapshot lengkap tanpa harus menutup database.
            val snapshotFile = File(context.cacheDir, "backup_snapshot.db")
            if (snapshotFile.exists()) snapshotFile.delete()
            try {
                val escaped = snapshotFile.absolutePath.replace("'", "''")
                db.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
            } catch (e: Exception) {
                SecureLog.e("BackupHelper", "VACUUM INTO gagal, backup dibatalkan agar tidak kehilangan data", e)
                TelemetryHelper.trackBackupAction("backup", false, "VACUUM INTO failed: ${e.message}")
                return@withContext BackupResult.Error("Gagal menyiapkan database untuk dicadangkan: ${e.localizedMessage}")
            }
            if (!snapshotFile.exists() || snapshotFile.length() == 0L) {
                SecureLog.e("BackupHelper", "Snapshot hasil VACUUM INTO kosong")
                return@withContext BackupResult.Error("Gagal menyiapkan database untuk dicadangkan: snapshot kosong.")
            }

            val prefix = if (isAuto) "auto_DataNanoMoney_" else "manual_DataNanoMoney_"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "$prefix$timestamp.db")

            // Pack both database file and shared preferences XMLs into a single ZIP-formatted archive
            ZipOutputStream(backupFile.outputStream()).use { zos ->
                // A. Add the consistent snapshot (VACUUM INTO), not the live .db file
                zos.putNextEntry(ZipEntry("database/financial_tracker_database"))
                snapshotFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()

                // B. Add all available shared preferences XML entries
                val dataDir = context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                SecureLog.d("BackupHelper", "Memulai penyalinan Shared Preferences dari: ${sharedPrefsDir.absolutePath}")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles { _, name -> name.endsWith(".xml") }?.forEach { xmlFile ->
                        SecureLog.d("BackupHelper", "Mencadangkan file preferensi: ${xmlFile.name}")
                        zos.putNextEntry(ZipEntry("shared_prefs/${xmlFile.name}"))
                        xmlFile.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                } else {
                    SecureLog.w("BackupHelper", "Folder shared_prefs tidak ditemukan atau bukan direktori: ${sharedPrefsDir.absolutePath}")
                }
            }

            cleanOldBackups(context)
            if (snapshotFile.exists()) snapshotFile.delete()

            SecureLog.d("BackupHelper", "Settings and database successfully backed up to ${backupFile.absolutePath}")
            TelemetryHelper.trackBackupAction("backup", true)
            BackupResult.Success(backupFile.name)
        } catch (e: Exception) {
            SecureLog.e("BackupHelper", "Error during backup: ${e.message}", e)
            val errMsg = e.localizedMessage ?: e.toString()
            TelemetryHelper.trackBackupAction("backup", false, errMsg)
            TelemetryHelper.logNonFatal(e, "Backup failed")
            BackupResult.Error(errMsg)
        }
    }

    fun getBackups(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file -> 
            file.name.endsWith(".db") && (
                file.name.startsWith("auto_backup_") ||
                file.name.startsWith("manual_backup_") ||
                file.name.startsWith("auto_DataNanoMoney_") ||
                file.name.startsWith("manual_DataNanoMoney_") ||
                file.name.startsWith("prerestore_DataNanoMoney_")
            )
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    private fun cleanOldBackups(context: Context) {
        try {
            val allBackups = getBackups(context)
            if (allBackups.size > 5) {
                for (i in 5 until allBackups.size) {
                    allBackups[i].delete()
                    SecureLog.d("BackupHelper", "Menghapus cadangan lokal lama untuk membatasi maksimal 5: ${allBackups[i].name}")
                }
            }
        } catch (e: Exception) {
            SecureLog.e("BackupHelper", "Gagal membersihkan cadangan lama: ${e.message}")
        }
    }

    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            val tempDbFile = File(context.cacheDir, "temp_restore.db")
            if (tempDbFile.exists()) {
                tempDbFile.delete()
            }

            if (isZipFile(backupFile)) {
                ZipInputStream(backupFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == "database/financial_tracker_database") {
                            tempDbFile.parentFile?.mkdirs()
                            // Decompression bomb guard: max 500MB for database
                            var bytesWritten = 0L
                            val maxBytes = 500L * 1024 * 1024
                            tempDbFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    bytesWritten += read
                                    if (bytesWritten > maxBytes) {
                                        SecureLog.w("BackupHelper", "Database file too large, aborting restore")
                                        break
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } else {
                backupFile.inputStream().use { input ->
                    tempDbFile.parentFile?.mkdirs()
                    tempDbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!tempDbFile.exists() || tempDbFile.length() == 0L) {
                throw IllegalStateException("File database cadangan tidak ditemukan atau kosong")
            }

            net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
            val passphraseBytes = DatabaseKeyManager.getOrCreatePassphrase(context)
            var restoredVersion = 0
            var isValid = false

            // Buka langsung lewat SQLCipher, bukan lewat SupportSQLiteOpenHelper.
            // Helper memaksa cocokkan user_version dengan versi Callback, sehingga
            // backup versi 5 selalu ditolak oleh Callback(1) padahal file-nya sehat.
            try {
                val probe = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    tempDbFile.absolutePath,
                    passphraseBytes,
                    null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE,
                    null,
                    null
                )
                try {
                    restoredVersion = probe.version
                    // Pastikan tabel benar-benar ada & bisa dibaca, bukan sekadar file valid.
                    probe.rawQuery("SELECT COUNT(*) FROM financial_records", null).use { c ->
                        if (c.moveToFirst()) c.getInt(0)
                    }
                    isValid = true
                } finally {
                    probe.close()
                }
            } catch (e: Exception) {
                SecureLog.e("BackupHelper", "Database validasi gagal: ${e.message}", e)
            } finally {
                java.util.Arrays.fill(passphraseBytes, 0.toByte())
            }

            if (!isValid) {
                if (tempDbFile.exists()) {
                    tempDbFile.delete()
                }
                throw IllegalStateException("Format database rusak atau kata sandi enkripsi salah (v$restoredVersion)")
            }

            AppDatabase.resetDatabaseInstance()

            val dbFile = context.getDatabasePath("financial_tracker_database")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            // -wal/-shm milik database LAMA. Hapus setelah file utama diganti,
            // kalau tidak SQLite akan memutar ulang log transaksi lama ke file
            // hasil restore sehingga data yang dikembalikan berubah lagi.
            if (dbFile.exists()) {
                // Jaring pengaman: simpan DB lama sebelum ditimpa, supaya data asli
                // masih bisa dipulihkan kalau ternyata hasil restore tidak bisa dibuka.
                try {
                    val safetyDir = getBackupDirectory(context)
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val safety = File(safetyDir, "prerestore_DataNanoMoney_$stamp.db")
                    dbFile.inputStream().use { input -> safety.outputStream().use { output -> input.copyTo(output) } }
                    SecureLog.w("BackupHelper", "DB lama disimpan sebelum restore: ${safety.name}")
                } catch (e: Exception) {
                    SecureLog.e("BackupHelper", "Gagal menyimpan salinan pengaman sebelum restore", e)
                }
                dbFile.delete()
            }
            dbFile.parentFile?.mkdirs()
            val moved = tempDbFile.renameTo(dbFile)
            if (!moved) {
                tempDbFile.inputStream().use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempDbFile.delete()
            }
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Whitelist: only restore known app settings prefs, never security-critical ones
            val allowedPrefsFiles = setOf(
                "financial_tracker_prefs.xml",
                "app_security_prefs.xml",
                "security_prefs.xml",
                "pin_prefs.xml"
            )

            if (isZipFile(backupFile)) {
                ZipInputStream(backupFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.startsWith("shared_prefs/")) {
                            val fileName = entry.name.substringAfter("shared_prefs/")
                            if (fileName !in allowedPrefsFiles) {
                                SecureLog.w("BackupHelper", "Skipping non-whitelisted prefs file: $fileName")
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }
                            val dataDir = context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir
                            val sharedPrefsDir = File(dataDir, "shared_prefs")
                            if (!sharedPrefsDir.exists()) {
                                sharedPrefsDir.mkdirs()
                            }
                            val targetPrefFile = File(sharedPrefsDir, fileName)
                            if (!targetPrefFile.canonicalPath.startsWith(sharedPrefsDir.canonicalPath + File.separator)) {
                                SecureLog.w("BackupHelper", "Zip Slip attack detected in entry: ${entry.name}")
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }
                            // Decompression bomb guard: max 1MB per shared_prefs file
                            var bytesWritten = 0L
                            val maxBytes = 1L * 1024 * 1024
                            targetPrefFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    bytesWritten += read
                                    if (bytesWritten > maxBytes) {
                                        SecureLog.w("BackupHelper", "Shared_prefs file too large, aborting: ${entry.name}")
                                        break
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            TelemetryHelper.trackBackupAction("restore", true)
            restartApplication(context)
            true
        } catch (e: Exception) {
            SecureLog.e("BackupHelper", "Gagal mengembalikan cadangan: ${e.message}", e)
            TelemetryHelper.trackBackupAction("restore", false, e.message)
            TelemetryHelper.logNonFatal(e, "Restore failed")
            false
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                val isZip = read == 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
                isZip
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
