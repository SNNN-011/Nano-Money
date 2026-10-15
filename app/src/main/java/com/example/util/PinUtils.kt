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
    private const val KEY_FAILED_COUNT = "pin_failed_count" // H3: gagal berurutan
    private const val KEY_LOCK_UNTIL = "pin_lock_until"     // H3: epoch ms
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
            remove(KEY_FAILED_COUNT) // H3: PIN baru = hitungan gagal direset
            remove(KEY_LOCK_UNTIL)
            commit()
        }
    }

    /**
     * H3: lama lockout bertingkat dari jumlah gagal berurutan.
     * 5 gagal → 30 detik, 10 gagal → 5 menit, 15 gagal → 30 menit. Tanpa wipe data user.
     */
    internal fun lockoutDurationMillis(failedCount: Int): Long = when {
        failedCount >= 15 -> 30 * 60_000L
        failedCount >= 10 -> 5 * 60_000L
        failedCount >= 5 -> 30_000L
        else -> 0L
    }

    /** H3: sisa waktu lockout (ms); 0 bila tidak sedang terkunci. */
    fun lockoutRemainingMillis(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCK_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        // H3: selama lockout verifikasi ditolak dan tidak menambah hitungan (anti-spam)
        if (lockoutRemainingMillis(context) > 0) return false
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
        if (match) {
            // H3: PIN benar → reset hitungan gagal + lockout
            p.edit().remove(KEY_FAILED_COUNT).remove(KEY_LOCK_UNTIL).commit()
            return true
        }
        // H3: catat gagal berurutan + pasang lockout (commit sinkron agar tidak hilang saat app ditutup)
        val failedCount = p.getInt(KEY_FAILED_COUNT, 0) + 1
        p.edit().putInt(KEY_FAILED_COUNT, failedCount).apply {
            val lockMillis = lockoutDurationMillis(failedCount)
            if (lockMillis > 0) putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + lockMillis)
            commit()
        }
        return false
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
            remove(KEY_FAILED_COUNT) // H3: bersihkan status lockout
            remove(KEY_LOCK_UNTIL)
            commit()
        }
    }

    /**
     * H4a: matikan PIN hanya bila PIN lama terverifikasi.
     * Return true bila PIN cocok dan PIN berhasil dimatikan.
     */
    fun disablePinIfVerified(context: Context, pin: String): Boolean {
        if (!verifyPin(context, pin)) return false
        disablePin(context)
        return true
    }

    fun changePin(context: Context, newPin: String) = savePin(context, newPin)
}
