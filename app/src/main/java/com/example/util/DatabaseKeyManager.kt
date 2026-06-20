package com.example.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "financial_tracker_keystore_key"
    private const val PREFS_NAME = "secure_db_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_db_passphrase"
    private const val KEY_IV = "db_passphrase_iv"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
                val ivBytes = Base64.decode(ivBase64, Base64.DEFAULT)
                val secretKey = getKeystoreKey() ?: throw IllegalStateException("Key not found in KeyStore")

                val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
                val spec = GCMParameterSpec(128, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                return cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                // Jika terjadi gagal dekripsi (e.g. KeyStore rusak / reset), kita bisa fallback/generate ulang
            }
        }

        val secureRandom = SecureRandom()
        val passphraseBytes = ByteArray(32)
        secureRandom.nextBytes(passphraseBytes)

        try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(passphraseBytes)

            val newEncryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val newIvBase64 = Base64.encodeToString(iv, Base64.DEFAULT)

            prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, newEncryptedBase64)
                .putString(KEY_IV, newIvBase64)
                .apply()
        } catch (e: Exception) {
            return getFallbackPassphrase(context)
        }

        return passphraseBytes
    }

    private fun getKeystoreKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        getKeystoreKey()?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getFallbackPassphrase(context: Context): ByteArray {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "fallback_secure_key_financial_tracker"

        val obfuscatedSalt = byteArrayOf(
            (0x5F xor 0x1C).toByte(),
            (0x3B xor 0x2A).toByte(),
            (0x7D xor 0x34).toByte(),
            (0x1E xor 0x48).toByte(),
            (0x43 xor 0x56).toByte(),
            (0x69 xor 0x21).toByte(),
            (0x28 xor 0x7E).toByte(),
            (0x0A xor 0x1B).toByte(),
            (0x56 or 0x10).toByte(),
            (0x2E xor 0x0A).toByte(),
            (0x6B xor 0x0B).toByte(),
            (0x1F xor 0x1C).toByte(),
            (0x35 xor 0x0D).toByte(),
            (0x4E xor 0x3E).toByte(),
            (0x7A xor 0x5F).toByte(),
            (0x11 xor 0x10).toByte()
        )

        return try {
            val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
            messageDigest.update(androidId.toByteArray(Charsets.UTF_8))
            messageDigest.update(obfuscatedSalt)
            messageDigest.digest()
        } catch (e: Exception) {
            androidId.toByteArray(Charsets.UTF_8).copyOf(32)
        }
    }
}
