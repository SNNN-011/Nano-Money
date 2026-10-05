package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Pilihan engine OCR struk yang disimpan user.
 *
 * Default-nya GEMINI supaya perilaku app tidak berubah bagi user yang belum
 * pernah menyentuh toggle ini, dan supaya proteksi API key di Cloudflare Worker
 * tetap jadi jalur utama sampai user sengaja memilih TYPELLM.
 */
enum class ReceiptEngine(val storageValue: String) {
    GEMINI("gemini"),
    TYPELLM("typellm");

    companion object {
        fun fromStorage(value: String?): ReceiptEngine =
            entries.firstOrNull { it.storageValue == value } ?: GEMINI
    }
}

object ReceiptEnginePrefs {

    private const val PREFS_NAME = "app_receipt_engine_prefs"
    private const val KEY_ENGINE = "receipt_engine"

    private fun prefs(context: Context): SharedPreferences =
        SecurePrefsHelper.getEncryptedPrefs(context.applicationContext, PREFS_NAME)

    fun get(context: Context): ReceiptEngine =
        ReceiptEngine.fromStorage(prefs(context).getString(KEY_ENGINE, null))

    fun set(context: Context, engine: ReceiptEngine) {
        prefs(context).edit().putString(KEY_ENGINE, engine.storageValue).apply()
    }
}