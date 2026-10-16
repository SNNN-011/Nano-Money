package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.example.util.SecureLog
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cadangan aplikasi: satu berkas ZIP berisi database + kunci DB + semua pengaturan.
 *
 * Desain (keputusan pemilik aplikasi):
 * - Tidak ada lagi cadangan lokal. ZIP dibuat di cache lalu diunggah ke Google
 *   Drive oleh pemanggil, setelah itu berkas sementara dihapus.
 * - ZIP sengaja TIDAK dikunci; kunci DB ikut di dalamnya supaya cadangan bisa
 *   dipulihkan di perangkat lain (bocor = tanggung jawab pengguna).
 * - PIN tidak ikut: hash-nya tidak ada gunanya di ZIP yang sudah memuat semuanya,
 *   dan PIN bekas bisa dicoba di tempat lain.
 */
object BackupHelper {

    private const val TAG = "BackupHelper"

    // Format 2: manifest + kunci DB + prefs terenkripsi sebagai teks.
    // Format 1 (lama) tidak punya entri baru; pemulihannya tetap didukung.
    private const val FORMAT_VERSION = 2
    private const val ENTRY_DB = "database/financial_tracker_database"
    private const val ENTRY_KEY = "db_key.txt"
    private const val ENTRY_MANIFEST = "manifest.json"
    private const val PREFIX_RAW_PREFS = "shared_prefs/"
    private const val PREFIX_JSON_PREFS = "prefs_json/"

    private const val MAX_DB_BYTES = 500L * 1024 * 1024
    private const val MAX_PREFS_BYTES = 1L * 1024 * 1024

    /**
     * Prefs yang TIDAK pernah ikut cadangan:
     * - sync_meta: posisi sinkron perangkat ini (kalau ikut, sinkronisasi kacau)
     * - secure_db_prefs: wadah kunci DB (kunci sendiri ikut lewat entri db_key.txt)
     * - pin_prefs: hash PIN (sengaja tidak dipindah antar perangkat)
     */
    private val EXCLUDED_PREFS = setOf("sync_meta", "secure_db_prefs", "pin_prefs")

    /**
     * Prefs terenkripsi. XML mentahnya terikat Keystore perangkat asal sehingga
     * TIDAK portabel; nilainya diekspor lewat API (lihat PrefsBackupCodec) lalu
     * diimpor ulang di perangkat tujuan.
     */
    private val ENCRYPTED_PREFS = listOf(
        "security_prefs",
        "app_security_prefs",
        "financial_tracker_prefs"
    )

    sealed interface BackupResult {
        data class Success(val file: File) : BackupResult
        data class Error(val message: String) : BackupResult
    }

    data class RestoreResult(val success: Boolean, val message: String)

    /** Nama berkas cadangan bertanggal: NanoMoney_backup_<stamp>.zip */
    fun newBackupFileName(): String =
        "NanoMoney_backup_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            ".zip"

    /**
     * Membuat ZIP cadangan di cache (bukan penyimpanan permanen). Pemanggil
     * bertanggung jawab menghapus berkasnya setelah selesai (unggah/manual).
     */
    suspend fun performBackup(context: Context): BackupResult = withContext(Dispatchers.IO) {
        var zipFile: File? = null
        try {
            val db = AppDatabase.getDatabase(context)

            // 1. Database tidak boleh sedang transaksi.
            val inTransaction = try {
                db.openHelper.writableDatabase.inTransaction()
            } catch (e: Exception) {
                false
            }
            if (inTransaction) {
                TelemetryHelper.trackBackupAction("backup", false, "Database is in active transaction")
                return@withContext BackupResult.Error("Database sedang dalam transaksi aktif oleh proses lain. Mohon tunggu sejenak.")
            }

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

            val dbFile = context.getDatabasePath("financial_tracker_database")
            if (!dbFile.exists()) {
                SecureLog.e(TAG, "Database file does not exist!")
                TelemetryHelper.trackBackupAction("backup", false, "Database file does not exist")
                return@withContext BackupResult.Error("Database file belum terbentuk! Harap isi minimal satu catatan keuangan terlebih dahulu.")
            }

            // 2. Siapkan direktori staging (selalu dibersihkan dulu).
            val stagingDir = File(context.cacheDir, "backup_staging")
            if (!stagingDir.exists()) stagingDir.mkdirs()
            stagingDir.listFiles()?.forEach { it.delete() }

            val dbSize = dbFile.length()
            val freeSpace = stagingDir.freeSpace
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

            // 3. Snapshot konsisten dengan VACUUM INTO. Database memakai journal WAL,
            // jadi menyalin file .db apa adanya bisa kehilangan transaksi terbaru.
            val snapshotFile = File(context.cacheDir, "backup_snapshot.db")
            if (snapshotFile.exists()) snapshotFile.delete()
            try {
                val escaped = snapshotFile.absolutePath.replace("'", "''")
                db.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
            } catch (e: Exception) {
                SecureLog.e(TAG, "VACUUM INTO gagal, backup dibatalkan agar tidak kehilangan data", e)
                TelemetryHelper.trackBackupAction("backup", false, "VACUUM INTO failed: ${e.message}")
                return@withContext BackupResult.Error("Gagal menyiapkan database untuk dicadangkan: ${e.localizedMessage}")
            }
            if (!snapshotFile.exists() || snapshotFile.length() == 0L) {
                SecureLog.e(TAG, "Snapshot hasil VACUUM INTO kosong")
                return@withContext BackupResult.Error("Gagal menyiapkan database untuk dicadangkan: snapshot kosong.")
            }

            val passphraseBytes = DatabaseKeyManager.getOrCreatePassphrase(context)
            val zip = File(stagingDir, newBackupFileName())
            zipFile = zip

            try {
                ZipOutputStream(zip.outputStream()).use { zos ->
                    // A. Manifest ringkas untuk diagnostik & kompatibilitas.
                    zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zos.write(buildManifest(context, probeDbVersion(snapshotFile, passphraseBytes)).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // B. Kunci database (polos, lihat catatan desain di atas).
                    zos.putNextEntry(ZipEntry(ENTRY_KEY))
                    zos.write(android.util.Base64.encodeToString(passphraseBytes, android.util.Base64.NO_WRAP).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // C. Snapshot database.
                    zos.putNextEntry(ZipEntry(ENTRY_DB))
                    snapshotFile.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()

                    // D. Prefs polos: salinan XML apa adanya. Prefs sensitif
                    // (EXCLUDED_PREFS) dan prefs terenkripsi tidak lewat sini.
                    val sharedPrefsDir = File(
                        context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir,
                        "shared_prefs"
                    )
                    if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                        sharedPrefsDir.listFiles { _, name -> name.endsWith(".xml") }?.forEach { xmlFile ->
                            val baseName = xmlFile.name.removeSuffix(".xml")
                            if (baseName in EXCLUDED_PREFS || baseName in ENCRYPTED_PREFS) return@forEach
                            zos.putNextEntry(ZipEntry("$PREFIX_RAW_PREFS${xmlFile.name}"))
                            xmlFile.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }
                    } else {
                        SecureLog.w(TAG, "Folder shared_prefs tidak ditemukan: ${sharedPrefsDir.absolutePath}")
                    }

                    // E. Prefs terenkripsi: nilai polos via codec (portabel lintas perangkat).
                    ENCRYPTED_PREFS.forEach { name ->
                        try {
                            val prefs = SecurePrefsHelper.getEncryptedPrefs(context, name)
                            val text = PrefsBridge.exportEncryptedPrefs(prefs)
                            zos.putNextEntry(ZipEntry("$PREFIX_JSON_PREFS$name"))
                            zos.write(text.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        } catch (e: Exception) {
                            // Satu prefs gagal tidak boleh menggagalkan seluruh cadangan.
                            SecureLog.e(TAG, "Gagal mengekspor prefs terenkripsi '$name'", e)
                        }
                    }
                }
            } finally {
                java.util.Arrays.fill(passphraseBytes, 0.toByte())
                if (snapshotFile.exists()) snapshotFile.delete()
            }

            SecureLog.d(TAG, "Cadangan dibuat: ${zip.absolutePath} (${zip.length()} byte)")
            TelemetryHelper.trackBackupAction("backup", true)
            BackupResult.Success(zip)
        } catch (e: Exception) {
            SecureLog.e(TAG, "Error during backup: ${e.message}", e)
            zipFile?.delete()
            val errMsg = e.localizedMessage ?: e.toString()
            TelemetryHelper.trackBackupAction("backup", false, errMsg)
            TelemetryHelper.logNonFatal(e, "Backup failed")
            BackupResult.Error(errMsg)
        }
    }

    private fun buildManifest(context: Context, dbVersion: Int): String {
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        return org.json.JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("appVersion", versionName)
            put("dbVersion", dbVersion)
            put("device", Build.MODEL ?: "")
        }.toString()
    }

    /** Versi skema DB di dalam snapshot; -1 bila tidak terbaca. */
    private fun probeDbVersion(snapshotFile: File, passphraseBytes: ByteArray): Int {
        return try {
            net.sqlcipher.database.SQLiteDatabase.openDatabase(
                snapshotFile.absolutePath,
                passphraseBytes,
                null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY,
                null,
                null
            ).use { it.version }
        } catch (e: Exception) {
            SecureLog.w(TAG, "Versi DB cadangan tidak terbaca: ${e.message}")
            -1
        }
    }

    /**
     * Memulihkan cadangan ZIP. Alur lama (impor kunci & prefs lintas perangkat)
     * menyusul di perubahan terpisah; di sini tetap jalur perangkat yang sama.
     */
    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            val tempDbFile = File(context.cacheDir, "temp_restore.db")
            if (tempDbFile.exists()) {
                tempDbFile.delete()
            }

            ZipInputStream(backupFile.inputStream()).use { zis ->
                extractEntryToFile(zis, tempDbFile, ENTRY_DB, MAX_DB_BYTES)
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
            // backup versi lama selalu ditolak oleh Callback(8) padahal file-nya sehat.
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
                    var total = -1
                    var visible = -1
                    probe.rawQuery("SELECT COUNT(*) FROM financial_records", null).use { c ->
                        if (c.moveToFirst()) total = c.getInt(0)
                    }
                    probe.rawQuery("SELECT COUNT(*) FROM financial_records WHERE isDeleted = 0", null).use { c ->
                        if (c.moveToFirst()) visible = c.getInt(0)
                    }
                    SecureLog.d(TAG, "VALIDASI: total=$total terlihat=$visible")
                    isValid = true
                } finally {
                    probe.close()
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Database validasi gagal: ${e.message}", e)
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
            // kalau tidak SQLite akan memutar ulang log transaksi lama.
            if (dbFile.exists()) {
                // Jaring pengaman: simpan DB lama sebelum ditimpa.
                try {
                    val safety = File(context.filesDir, "prerestore_safety.db")
                    if (safety.exists()) safety.delete()
                    dbFile.inputStream().use { input -> safety.outputStream().use { output -> input.copyTo(output) } }
                    SecureLog.w(TAG, "DB lama disimpan sebelum restore: ${safety.absolutePath}")
                } catch (e: Exception) {
                    SecureLog.e(TAG, "Gagal menyimpan salinan pengaman sebelum restore", e)
                }
                dbFile.delete()
            }
            dbFile.parentFile?.mkdirs()
            val moved = tempDbFile.renameTo(dbFile)
            if (!moved) {
                tempDbFile.inputStream().use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempDbFile.delete()
            }
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Whitelist: hanya prefs yang dikenal & aman yang ditulis balik.
            val allowedPrefsFiles = setOf(
                "financial_tracker_prefs.xml",
                "app_security_prefs.xml",
                "security_prefs.xml"
            )

            ZipInputStream(backupFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.startsWith(PREFIX_RAW_PREFS)) {
                        val fileName = entry.name.substringAfter(PREFIX_RAW_PREFS)
                        if (fileName !in allowedPrefsFiles) {
                            SecureLog.w(TAG, "Skipping non-whitelisted prefs file: $fileName")
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
                            SecureLog.w(TAG, "Zip Slip attack detected in entry: ${entry.name}")
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        writeEntryToFile(zis, targetPrefFile, MAX_PREFS_BYTES)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Data hasil restore harus menang atas versi yang ada di Firestore.
            RestoreSyncMarker.markPending(context)

            TelemetryHelper.trackBackupAction("restore", true)
            restartApplication(context)
            true
        } catch (e: Exception) {
            SecureLog.e(TAG, "Gagal mengembalikan cadangan: ${e.message}", e)
            TelemetryHelper.trackBackupAction("restore", false, e.message)
            TelemetryHelper.logNonFatal(e, "Restore failed")
            false
        }
    }

    // ---- Utilitas entri ZIP (semua dengan batas ukuran anti decompression bomb) ----

    private fun extractEntryToFile(zis: ZipInputStream, target: File, wantedName: String, maxBytes: Long) {
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == wantedName) {
                writeEntryToFile(zis, target, maxBytes)
                return
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    private fun writeEntryToFile(zis: ZipInputStream, target: File, maxBytes: Long) {
        target.parentFile?.mkdirs()
        var bytesWritten = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var read: Int
            while (zis.read(buffer).also { read = it } != -1) {
                bytesWritten += read
                if (bytesWritten > maxBytes) {
                    SecureLog.w(TAG, "Entri cadangan melebihi batas ukuran: ${target.name}")
                    break
                }
                output.write(buffer, 0, read)
            }
        }
    }

    @Suppress("unused")
    private fun readEntryBytes(zis: ZipInputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        var read: Int
        while (zis.read(buffer).also { read = it } != -1) {
            total += read
            if (total > maxBytes) {
                throw IllegalStateException("Entri cadangan melebihi batas ukuran")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
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

/**
 * Jembatan tipis ke SecurePrefsHelper supaya BackupHelper tetap bisa diuji dan
 * tidak menyebarkan pemanggilan prefs terenkripsi ke mana-mana.
 */
private object PrefsBridge {
    fun exportEncryptedPrefs(prefs: android.content.SharedPreferences): String =
        PrefsBackupCodec.export(prefs)
}
