package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.util.SecureLog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Aturan keselamatan file prefs terenkripsi. Dipisah dari kode Android supaya bisa diuji.
 *
 * Latar (bug 2026-10-17): EncryptedSharedPreferences menyimpan keyset-nya di file yang
 * SAMA dengan datanya. Versi lama [SecurePrefsHelper] memindai `getSharedPreferences(name)`
 * lalu menulis ulang setiap entri hasil scan itu ke EncryptedSharedPreferences. Untuk file
 * yang sudah terenkripsi, artinya ciphertext dibaca lalu dienkripsi ULANG ke file yang sama:
 * file membengkak ~1,34x setiap kali aplikasi dibuka (flag penanda migrasi ikut terhapus
 * oleh cleanup-nya sendiri sehingga tidak ada yang menghentikannya) sampai heap penuh,
 * lalu aplikasi crash OutOfMemoryError dan tidak bisa dibuka sama sekali.
 */
internal object PrefsGuard {
    /** Penanda keyset EncryptedSharedPreferences. Kalau ada, isi file sudah terenkripsi. */
    const val KEYSET_MARKER = "__androidx_security_crypto_encrypted_prefs_key_keyset__"

    /** Batas wajar ukuran file prefs (byte). Di atas ini file dianggap rusak dan direset. */
    const val MAX_PREFS_BYTES = 4L * 1024 * 1024

    /**
     * Migrasi plain -> terenkripsi hanya boleh jalan kalau file-nya benar-benar plain
     * ([hasKeysetMarker] false) dan belum pernah dimigrasi ([migrationDone] false).
     */
    fun canMigrate(hasKeysetMarker: Boolean, migrationDone: Boolean): Boolean =
        !hasKeysetMarker && !migrationDone

    fun isOversized(fileBytes: Long): Boolean = fileBytes > MAX_PREFS_BYTES
}

object SecurePrefsHelper {
    private const val TAG = "SecurePrefsHelper"
    private val cache = mutableMapOf<String, SharedPreferences>()

    @Synchronized
    fun getEncryptedPrefs(context: Context, name: String): SharedPreferences {
        cache[name]?.let { return it }
        val prefs = createEncryptedPrefsWithRecovery(context, name)
        cache[name] = prefs
        return prefs
    }

    private fun createEncryptedPrefsWithRecovery(context: Context, name: String): SharedPreferences {
        // File yang membengkak (bug migrasi 2026-10-17) TIDAK boleh dibaca sama sekali:
        // memuat XML-nya menghabiskan heap sebelum recovery sempat jalan. Hapus langsung,
        // tanpa memuat isinya (belum ada instance SharedPreferences untuk nama ini).
        val rawFile = rawPrefsFile(context, name)
        val rawSize = rawFile.length()
        if (PrefsGuard.isOversized(rawSize)) {
            if (rawFile.delete()) {
                SecureLog.w(TAG, "File prefs '$name' $rawSize byte (membengkak/rusak) dihapus; dibuat ulang kosong.")
            } else {
                SecureLog.e(TAG, "Gagal menghapus file prefs rusak '$name' ($rawSize byte).")
            }
        }
        try {
            val prefs = buildEncryptedPrefs(context, name)
            prefs.all // paksa baca sekarang, supaya error Keystore ketahuan di sini, bukan nanti
            return prefs
        } catch (e: Exception) {
            SecureLog.e(TAG, "EncryptedSharedPreferences '$name' corrupt (${e.javaClass.simpleName}), melakukan reset file ini saja.", e)
            // M19: perbaikan dijalankan di Dispatchers.IO (I/O file + generate kunci tidak di main
            // thread) dan HANYA menyentuh file prefs '$name'. Master key global TIDAK dihapus agar
            // EncryptedSharedPreferences lain (security_prefs, financial_tracker_prefs, dst.) tetap bisa dibuka.
            // Nilai di prefs ini memang tidak bisa diselamatkan (file sudah korup) — user perlu set ulang
            // pengaturan di dalamnya, bukan dihapus seluruh app data.
            return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Bersihkan isi mentah file korup (cache SharedPreferences ikut kosong) lalu
                    // hapus file-nya — rebuild benar-benar dari nol, hanya untuk prefs INI.
                    context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit().clear().commit()
                    if (rawFile.exists() && !rawFile.delete()) {
                        SecureLog.w(TAG, "Gagal menghapus file korup '$name', rebuild tetap dilanjutkan")
                    }
                } catch (cleanupError: Exception) {
                    SecureLog.e(TAG, "Gagal cleanup file corrupt: ${cleanupError.message}", cleanupError)
                }
                try {
                    val newPrefs = buildEncryptedPrefs(context, name)
                    SecureLog.w(TAG, "EncryptedPrefs '$name' berhasil di-rebuild kosong.")
                    newPrefs
                } catch (e2: Exception) {
                    SecureLog.e(TAG, "Gagal total membuat ulang encrypted prefs '$name'.", e2)
                    throw IllegalStateException("Cannot create encrypted prefs '$name' after recovery", e2)
                }
            }
        }
    }

    /** File XML mentah milik prefs [name]; dipakai untuk cek ukuran dan reset. */
    private fun rawPrefsFile(context: Context, name: String) =
        java.io.File(context.applicationContext.filesDir.parentFile, "shared_prefs/$name.xml")

    private fun buildEncryptedPrefs(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val migrationKey = "security_migration_completed_v1"
        val plainPrefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        // Kalau keyset EncryptedSharedPreferences sudah ada di file ini, isinya sudah
        // terenkripsi: migrasi WAJIB dilewati (lihat komentar PrefsGuard).
        val hasKeysetMarker = plainPrefs.contains(PrefsGuard.KEYSET_MARKER)
        if (PrefsGuard.canMigrate(hasKeysetMarker, encryptedPrefs.getBoolean(migrationKey, false))) {
            val allEntries = plainPrefs.all
            if (allEntries.isNotEmpty()) {
                val editor = encryptedPrefs.edit()
                for ((key, value) in allEntries) {
                    if (key != migrationKey && !key.startsWith("__androidx_security_crypto_")) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                editor.putStringSet(key, value as? Set<String>)
                            }
                        }
                    }
                }
                editor.putBoolean(migrationKey, true)
                editor.apply()
                // M19: hapus HANYA key plain yang sudah dimigrasikan. clear() penuh di file yang
                // sama berisiko ikut menghapus entri keyset EncryptedSharedPreferences.
                val cleanup = plainPrefs.edit()
                for (key in allEntries.keys) {
                    cleanup.remove(key)
                }
                cleanup.apply()
            } else {
                encryptedPrefs.edit().putBoolean(migrationKey, true).apply()
            }
        }

        return encryptedPrefs
    }
}
