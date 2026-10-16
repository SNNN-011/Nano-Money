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
     * Memulihkan cadangan ZIP (format 2, atau format 1 lama).
     *
     * Urutan aman:
     *  1. ekstrak database + kunci + prefs ke cache, validasi database
     *     (kunci perangkat saat ini -> kunci dari ZIP -> mode polos);
     *  2. kalau perlu, impor kunci dari ZIP lalu BUKTIKAN kunci tersimpan;
     *  3. salin pengaman DB lama, tukar berkas, verifikasi ulang;
     *  4. tulis balik prefs (XML polos + prefs terenkripsi lewat API);
     *  5. tandai hasil restore untuk sinkronisasi, lalu mulai ulang aplikasi.
     */
    suspend fun restoreBackup(context: Context, backupFile: File): RestoreResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_unpack")
        try {
            tempDir.deleteRecursively()
            tempDir.mkdirs()
            val tempDbFile = File(tempDir, "financial_tracker_database")
            var keyBase64: String? = null
            val rawPrefs = LinkedHashMap<String, ByteArray>()
            val jsonPrefs = LinkedHashMap<String, ByteArray>()

            ZipInputStream(backupFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name
                        when {
                            entryName == ENTRY_DB ->
                                writeEntryToFile(zis, tempDbFile, MAX_DB_BYTES)

                            entryName == ENTRY_KEY ->
                                keyBase64 = String(readEntryBytes(zis, 8 * 1024), Charsets.UTF_8).trim()

                            entryName == ENTRY_MANIFEST -> try {
                                readEntryBytes(zis, 64 * 1024) // metadata; tidak dipakai saat pulih
                            } catch (e: Exception) {
                                // Manifest hanya diagnostik; entri terlalu besar tidak fatal.
                            }

                            entryName.startsWith(PREFIX_RAW_PREFS) -> {
                                val fileName = entryName.substringAfter(PREFIX_RAW_PREFS)
                                val baseName = fileName.removeSuffix(".xml")
                                if (fileName.endsWith(".xml") && isSafeFileName(fileName) &&
                                    baseName !in EXCLUDED_PREFS && baseName !in ENCRYPTED_PREFS
                                ) {
                                    rawPrefs[fileName] = readEntryBytes(zis, MAX_PREFS_BYTES)
                                }
                            }

                            entryName.startsWith(PREFIX_JSON_PREFS) -> {
                                val baseName = entryName.substringAfter(PREFIX_JSON_PREFS)
                                if (baseName in ENCRYPTED_PREFS && isSafeFileName(baseName)) {
                                    jsonPrefs[baseName] = readEntryBytes(zis, MAX_PREFS_BYTES)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!tempDbFile.exists() || tempDbFile.length() == 0L) {
                throw IllegalStateException("File database di dalam cadangan tidak ditemukan atau kosong.")
            }

            net.sqlcipher.database.SQLiteDatabase.loadLibs(context)

            // Database polos (dari versi lama) dikenali dari header SQLite;
            // database SQLCipher tidak punya header itu.
            val header = ByteArray(16)
            tempDbFile.inputStream().use { it.read(header) }
            val isPlaintextDb = String(header, Charsets.US_ASCII).startsWith("SQLite format 3")

            val zipKeyBytes: ByteArray? = keyBase64?.let {
                try {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    null
                }
            }

            var probe: DbProbe? = null
            var keyToImport: ByteArray? = null
            var plaintextRestore = false

            if (isPlaintextDb) {
                probe = probeDatabase(tempDbFile, ByteArray(0))
                plaintextRestore = true
            } else {
                val currentPassphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
                probe = probeDatabase(tempDbFile, currentPassphrase)
                if (probe == null && zipKeyBytes != null) {
                    // Kunci berbeda: cadangan berasal dari perangkat lain.
                    probe = probeDatabase(tempDbFile, zipKeyBytes)
                    if (probe != null) keyToImport = zipKeyBytes
                }
            }

            if (probe == null) {
                throw IllegalStateException("Database di dalam cadangan tidak bisa dibuka: berkas rusak atau kunci tidak cocok.")
            }
            SecureLog.d(TAG, "CADANGAN: versi=${probe.version} total=${probe.total} terlihat=${probe.visible} polos=$plaintextRestore")

            // Impor kunci SEBELUM menimpa apa pun, dan buktikan tersimpan —
            // kalau gagal, database lama masih utuh dan pemulihan dibatalkan.
            if (keyToImport != null) {
                DatabaseKeyManager.importPassphrase(context, keyToImport)
                val reprobed = DatabaseKeyManager.getOrCreatePassphrase(context)
                if (!reprobed.contentEquals(keyToImport)) {
                    throw IllegalStateException("Kunci cadangan gagal disimpan di perangkat ini; pemulihan dibatalkan.")
                }
                SecureLog.d(TAG, "Kunci database cadangan berhasil diimpor.")
            }

            AppDatabase.resetDatabaseInstance()

            val dbFile = context.getDatabasePath("financial_tracker_database")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (dbFile.exists()) {
                // Jaring pengaman terakhir: simpan DB lama sebelum ditimpa.
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
            if (!tempDbFile.renameTo(dbFile)) {
                tempDbFile.inputStream().use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            // -wal/-shm milik database LAMA: kalau tidak dihapus, SQLite memutar
            // ulang log transaksi lama di atas database hasil pemulihan.
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Verifikasi berkas di tempat tujuan, dengan kunci yang kini berlaku.
            val verifyKey = if (plaintextRestore) ByteArray(0) else DatabaseKeyManager.getOrCreatePassphrase(context)
            val verify = probeDatabase(dbFile, verifyKey)
                ?: throw IllegalStateException("Database hasil pemulihan tidak dapat dibuka kembali.")

            // Prefs polos: salinan XML apa adanya.
            val dataDir = context.applicationContext.dataDir ?: context.filesDir.parentFile ?: context.dataDir
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            if (!sharedPrefsDir.exists()) sharedPrefsDir.mkdirs()
            rawPrefs.forEach { (fileName, bytes) ->
                try {
                    File(sharedPrefsDir, fileName).writeBytes(bytes)
                } catch (e: Exception) {
                    SecureLog.e(TAG, "Gagal memulihkan prefs '$fileName'", e)
                }
            }

            // Prefs terenkripsi: tulis lewat API supaya dienkripsi ulang dengan
            // Keystore perangkat ini (XML mentahnya tidak portabel).
            var prefsRestored = 0
            jsonPrefs.forEach { (name, bytes) ->
                try {
                    val prefs = SecurePrefsHelper.getEncryptedPrefs(context, name)
                    prefsRestored += PrefsBackupCodec.import(prefs, String(bytes, Charsets.UTF_8))
                } catch (e: Exception) {
                    SecureLog.e(TAG, "Gagal memulihkan prefs terenkripsi '$name'", e)
                }
            }

            // Hasil restore tidak boleh kalah dari versi lama di Firestore.
            RestoreSyncMarker.markPending(context)

            TelemetryHelper.trackBackupAction("restore", true)
            SecureLog.d(
                TAG,
                "Pemulihan selesai: versi=${verify.version} baris=${verify.total} prefs_terenkripsi=$prefsRestored"
            )

            restartApplication(context)
            RestoreResult(
                success = true,
                message = "Cadangan dipulihkan: ${verify.visible} transaksi aktif (versi skema ${verify.version})."
            )
        } catch (e: Exception) {
            SecureLog.e(TAG, "Gagal memulihkan cadangan: ${e.message}", e)
            TelemetryHelper.trackBackupAction("restore", false, e.message)
            TelemetryHelper.logNonFatal(e, "Restore failed")
            RestoreResult(false, e.localizedMessage ?: "Pemulihan gagal.")
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                // abaikan: cache akan dibersihkan sistem nanti
            }
        }
    }

    // ---- Utilitas entri ZIP (semua dengan batas ukuran anti decompression bomb) ----

    private data class DbProbe(val version: Int, val total: Int, val visible: Int)

    /** Coba buka database; null kalau kunci salah atau berkas rusak. */
    private fun probeDatabase(dbFile: File, passphrase: ByteArray): DbProbe? {
        return try {
            val probe = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                var total = -1
                var visible = -1
                probe.rawQuery("SELECT COUNT(*) FROM financial_records", null).use { c ->
                    if (c.moveToFirst()) total = c.getInt(0)
                }
                probe.rawQuery("SELECT COUNT(*) FROM financial_records WHERE isDeleted = 0", null).use { c ->
                    if (c.moveToFirst()) visible = c.getInt(0)
                }
                DbProbe(probe.version, total, visible)
            } finally {
                probe.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

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
