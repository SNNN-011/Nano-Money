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
            SecureLog.e(TAG, "EncryptedSharedPreferences '$name' corrupt (${e.javaClass.simpleName}), melakukan reset paksa.", e)
            // Cleanup: hapus file corrupt, Keystore alias, dan plain prefs sisa migrasi
            // Tidak backup PIN dari XML — nilai di encrypted XML sudah terenkripsi,
            // restore ke encrypted baru = double-encryption = verify gagal.
            // User perlu set ulang PIN setelah recovery.
            try {
                context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
                val prefsFile = java.io.File(context.applicationContext.filesDir.parentFile, "shared_prefs/$name.xml")
                if (prefsFile.exists()) prefsFile.delete()
            } catch (cleanupError: Exception) {
                SecureLog.e(TAG, "Gagal cleanup file corrupt: ${cleanupError.message}", cleanupError)
            }
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (ksError: Exception) {
                SecureLog.e(TAG, "Gagal hapus Keystore alias: ${ksError.message}", ksError)
            }
            // Hapus plain prefs sisa migrasi lama — hindari window plaintext
            try {
                context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            } catch (_: Exception) {}
            return try {
                val newPrefs = buildEncryptedPrefs(context, name)
                SecureLog.w(TAG, "EncryptedPrefs '$name' berhasil di-rebuild. PIN perlu di-set ulang.")
                newPrefs
            } catch (e2: Exception) {
                SecureLog.e(TAG, "Gagal total membuat ulang encrypted prefs '$name'.", e2)
                throw IllegalStateException("Cannot create encrypted prefs '$name' after recovery", e2)
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
                plainPrefs.edit().clear().apply()
            } else {
                encryptedPrefs.edit().putBoolean(migrationKey, true).apply()
            }
        }

        return encryptedPrefs
    }
}
