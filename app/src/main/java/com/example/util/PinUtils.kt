package com.example.util

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinUtils {
    private const val PREFS_NAME = "pin_prefs"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_ENABLED = "pin_enabled"
    private const val PBKDF2_ITERATIONS = 310_000 // OWASP 2023 recommendation for PBKDF2WithHmacSHA256
    private const val PBKDF2_KEY_LENGTH = 256

    // Plain SharedPreferences — PIN sudah one-way hash (PBKDF2 + salt),
    // EncryptedSharedPreferences redundant dan data loss di cold start (MIUI/dll)
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPin(pin: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(hash)
    }

    fun savePin(context: Context, pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs(context).edit().apply {
            putString(KEY_HASH, hash)
            putString(KEY_SALT, salt)
            putBoolean(KEY_ENABLED, true)
            commit()
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val storedHash = p.getString(KEY_HASH, null)
        val salt = p.getString(KEY_SALT, null)
        SecureLog.d("PinUtils", "verifyPin: hasHash=${storedHash != null}, hasSalt=${salt != null}")
        if (storedHash == null || salt == null) return false
        val computed = hashPin(pin, salt)
        val match = java.security.MessageDigest.isEqual(
            storedHash.toByteArray(Charsets.UTF_8),
            computed.toByteArray(Charsets.UTF_8)
        )
        SecureLog.d("PinUtils", "verifyPin: match=$match")
        return match
    }

    fun isPinEnabled(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_ENABLED, false) && p.getString(KEY_HASH, null) != null
    }

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_HASH, null) != null

    fun disablePin(context: Context) {
        prefs(context).edit().apply {
            remove(KEY_HASH)
            remove(KEY_SALT)
            putBoolean(KEY_ENABLED, false)
            commit()
        }
    }

    fun changePin(context: Context, newPin: String) = savePin(context, newPin)
}