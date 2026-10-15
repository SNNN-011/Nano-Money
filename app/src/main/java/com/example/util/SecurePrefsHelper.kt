package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.util.SecureLog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
                    val prefsFile = java.io.File(context.applicationContext.filesDir.parentFile, "shared_prefs/$name.xml")
                    if (prefsFile.exists() && !prefsFile.delete()) {
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
        if (!encryptedPrefs.getBoolean(migrationKey, false)) {
            val plainPrefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
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
