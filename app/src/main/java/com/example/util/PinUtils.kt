package com.example.util

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PinUtils {
    private const val PREFS_NAME = "app_security_prefs"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_ENABLED = "pin_enabled"

    private fun prefs(context: Context) =
        SecurePrefsHelper.getEncryptedPrefs(context, PREFS_NAME)

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
        prefs(context).edit().apply {
            putString(KEY_HASH, hash)
            putString(KEY_SALT, salt)
            putBoolean(KEY_ENABLED, true)
            apply()
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val storedHash = p.getString(KEY_HASH, null) ?: return false
        val salt = p.getString(KEY_SALT, null) ?: return false
        return storedHash == hashPin(pin, salt)
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
            apply()
        }
    }

    fun changePin(context: Context, newPin: String) = savePin(context, newPin)
}