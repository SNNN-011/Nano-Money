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
    private const val KEY_FALLBACK_PASSPHRASE = "db_passphrase_fallback_b64"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"

    private const val TAG = "DatabaseKeyManager"

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
                SecureLog.e(TAG, "Gagal mendekripsi passphrase database dari KeyStore.", e)
                throw IllegalStateException("Gagal mendekripsi kunci database terenkripsi. Keystore perangkat mungkin telah di-reset.", e)
            }
        }

        // Kunci hasil impor pemulihan saat Keystore gagal (lihat importPassphrase).
        // ponytail: jalur ini polos demi data tetap terbuka setelah restore;
        // cabut bila keputusan M18 (fallback passphrase) berubah jadi fail-closed.
        prefs.getString(KEY_FALLBACK_PASSPHRASE, null)?.let { plainBase64 ->
            try {
                return Base64.decode(plainBase64, Base64.DEFAULT)
            } catch (e: Exception) {
                SecureLog.e(TAG, "Fallback passphrase polos rusak; kunci baru akan dibuat.", e)
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

            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, newEncryptedBase64)
                .putString(KEY_IV, newIvBase64)
                .commit()

            if (!committed) {
                SecureLog.e(TAG, "Gagal menyimpan passphrase terenkripsi ke SharedPreferences (commit returned false).")
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Gagal membuat/menyimpan key di KeyStore, menggunakan fallback passphrase.", e)
            return getFallbackPassphrase(context)
        }

        return passphraseBytes
    }

    /**
     * Menyimpan kunci DB hasil pemulihan cadangan ke perangkat ini.
     *
     * Jalur normal sama dengan pembuatan kunci: dienkripsi Keystore. Kalau
     * Keystore sedang bermasalah, kunci disimpan polos (base64) supaya
     * database hasil pemulihan tetap bisa dibuka — data terbaca lebih penting
     * daripada menyembunyikan kunci di perangkat yang keystore-nya rusak.
     */
    @Synchronized
    fun importPassphrase(context: Context, passphrase: ByteArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(passphrase)
            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                .remove(KEY_FALLBACK_PASSPHRASE)
                .commit()
            if (committed) return
            SecureLog.e(TAG, "commit prefs gagal saat impor kunci; memakai fallback polos.")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Keystore gagal saat impor kunci; memakai fallback polos.", e)
        }
        prefs.edit()
            .remove(KEY_ENCRYPTED_PASSPHRASE)
            .remove(KEY_IV)
            .putString(KEY_FALLBACK_PASSPHRASE, Base64.encodeToString(passphrase, Base64.DEFAULT))
            .commit()
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
            val digest = messageDigest.digest()
            java.util.Arrays.fill(obfuscatedSalt, 0.toByte())
            digest
        } catch (e: Exception) {
            val fallbackBytes = androidId.toByteArray(Charsets.UTF_8).copyOf(32)
            java.util.Arrays.fill(obfuscatedSalt, 0.toByte())
            fallbackBytes
        }
    }
}
