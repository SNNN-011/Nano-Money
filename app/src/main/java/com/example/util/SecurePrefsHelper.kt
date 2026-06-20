package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePrefsHelper {
    private const val TAG = "SecurePrefsHelper"

    fun getEncryptedPrefs(context: Context, name: String): SharedPreferences {
        val masterKeyAlias = try {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mendapatkan MasterKey: ${e.message}", e)
            return context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        val encryptedPrefs = try {
            EncryptedSharedPreferences.create(
                name,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menginisialisasi EncryptedSharedPreferences untuk $name: ${e.message}", e)
            return context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        try {
            val migrationKey = "security_migration_completed_v1"
            if (!encryptedPrefs.getBoolean(migrationKey, false)) {
                val plainPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val allEntries = plainPrefs.all
                if (allEntries.isNotEmpty()) {
                    val editor = encryptedPrefs.edit()
                    for ((key, value) in allEntries) {
                        if (key != migrationKey) {
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
                    Log.d(TAG, "Migrasi data Shared Preferences untuk file '$name' berhasil diselesaikan.")
                } else {
                    encryptedPrefs.edit().putBoolean(migrationKey, true).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan dalam proses migrasi preferensi: ${e.message}", e)
        }

        return encryptedPrefs
    }
}
