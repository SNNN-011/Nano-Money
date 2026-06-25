package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PinHashHelper {
    
    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashValue(value: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = value + salt
        val hash = md.digest(combined.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyValue(input: String, storedHash: String, storedSalt: String): Boolean {
        if (storedHash.isEmpty() || storedSalt.isEmpty()) return false
        val newHash = hashValue(input, storedSalt)
        val hashBytes1 = Base64.decode(storedHash, Base64.NO_WRAP)
        val hashBytes2 = Base64.decode(newHash, Base64.NO_WRAP)
        return MessageDigest.isEqual(hashBytes1, hashBytes2)
    }
}
