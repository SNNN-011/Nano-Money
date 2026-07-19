package com.example.util

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PinUtils {
    private const val PREFS_NAME = "pin_prefs"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_ENABLED = "pin_enabled"

    // Plain SharedPreferences — PIN sudah one-way hash (SHA-256 + salt),
    // EncryptedSharedPreferences redundant dan data loss di cold start (MIUI/dll)
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPin(pin: String, salt: String): String {
        val hash = sha256(pin + salt)
        return Base64.getEncoder().encodeToString(hash)
    }

    fun savePin(context: Context, pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        android.util.Log.d("PinUtils", "savePin: saving hash=${hash.take(10)}... salt=${salt.take(10)}...")
        prefs(context).edit().apply {
            putString(KEY_HASH, hash)
            putString(KEY_SALT, salt)
            putBoolean(KEY_ENABLED, true)
            apply()
        }
        // Verify immediately
        val p = prefs(context)
        android.util.Log.d("PinUtils", "savePin verify: enabled=${p.getBoolean(KEY_ENABLED, false)}, hasHash=${p.getString(KEY_HASH, null) != null}")
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val storedHash = p.getString(KEY_HASH, null) ?: return false
        val salt = p.getString(KEY_SALT, null) ?: return false
        return storedHash == hashPin(pin, salt)
    }

    fun isPinEnabled(context: Context): Boolean {
        val p = prefs(context)
        val enabled = p.getBoolean(KEY_ENABLED, false)
        val hasHash = p.getString(KEY_HASH, null) != null
        android.util.Log.d("PinUtils", "isPinEnabled: enabled=$enabled, hasHash=$hasHash")
        return enabled && hasHash
    }

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun disablePin(context: Context) {
        prefs(context).edit().apply {
            remove(KEY_HASH)
            remove(KEY_SALT)
            putBoolean(KEY_ENABLED, false)
            apply()
        }
    }

    fun changePin(context: Context, newPin: String) = savePin(context, newPin)
}