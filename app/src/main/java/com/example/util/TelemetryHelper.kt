package com.example.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryHelper {

    private const val PREFS_NAME = "telemetry_prefs"
    private const val KEY_ENABLED = "telemetry_enabled"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var firebaseCrashlytics: FirebaseCrashlytics? = null

    // M15: telemetri default OFF + pref lokal otoritatif. Remote Config TIDAK boleh menyalakan.
    private var telemetryOptedInLocally = false

    fun initialize(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseCrashlytics = FirebaseCrashlytics.getInstance()

            val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            // M15: baca opt-in lokal (default false). Belum ada toggle UI — lihat laporan refactor.
            telemetryOptedInLocally = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
            applyCollectionEnabled(telemetryOptedInLocally)

            logCrashlytics("TelemetryHelper initialized successfully. IsDebug: $isDebug, optedIn: $telemetryOptedInLocally")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** M15: status opt-in lokal — dipakai RemoteConfigHelper untuk mencerminkan keadaan nyata. */
    fun isOptedInLocally(): Boolean = telemetryOptedInLocally

    /**
     * M15: satu-satunya jalur menyalakan telemetri adalah opt-in lokal user.
     * Remote Config tidak boleh memanggil ini untuk menyalakan.
     */
    fun setLocalOptIn(context: Context, enabled: Boolean) {
        telemetryOptedInLocally = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        applyCollectionEnabled(enabled)
    }

    private fun applyCollectionEnabled(enabled: Boolean) {
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
            firebaseCrashlytics?.setCrashlyticsCollectionEnabled(enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            // L14: event tidak lagi digandakan sebagai log Crashlytics (biaya + duplikasi data)
            firebaseAnalytics?.logEvent(eventName, params)
        } catch (e: Exception) {
            logNonFatal(e, "Gagal mencatat event: $eventName")
        }
    }

    fun logCrashlytics(message: String) {
        try {
            firebaseCrashlytics?.log(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logNonFatal(exception: Throwable, customMessage: String? = null) {
        try {
            customMessage?.let {
                firebaseCrashlytics?.log(it)
            }
            firebaseCrashlytics?.recordException(exception)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setUserProperty(propertyName: String, value: String) {
        try {
            firebaseAnalytics?.setUserProperty(propertyName, value)
            firebaseCrashlytics?.setCustomKey(propertyName, value)
        } catch (e: Exception) {
            logNonFatal(e, "Gagal menyimpan user property: $propertyName")
        }
    }

    fun setUserId(userId: String) {
        try {
            val hashedId = hashStringSha256(userId)
            firebaseAnalytics?.setUserId(hashedId)
            firebaseCrashlytics?.setUserId(hashedId)
        } catch (e: Exception) {
            logNonFatal(e, "Gagal menset userId")
        }
    }

    private fun hashStringSha256(input: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "anonymous_user"
        }
    }

    fun trackScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun trackTransactionAction(action: String, type: String, category: String, amount: Long) {
        // M15: kirim bucket kasar saja — tanpa teks kategori mentah / nominal mentah
        val amountTier = when {
            amount < 10_000L -> "LT10K"
            amount < 100_000L -> "LT100K"
            amount < 1_000_000L -> "LT1M"
            amount < 10_000_000L -> "LT10M"
            else -> "GTE10M"
        }
        val bundle = Bundle().apply {
            putString("transaction_action", action)
            putString("transaction_type", type)
            putBoolean("transaction_category_present", category.isNotBlank())
            putString("transaction_amount_tier", amountTier)
        }
        logEvent("transaction_management", bundle)
    }

    fun trackSecurityCheck(status: String, isSafe: Boolean) {
        val bundle = Bundle().apply {
            putString("security_status_value", status)
            putBoolean("is_device_safe", isSafe)
        }
        logEvent("app_security_check", bundle)
    }

    fun trackBackupAction(action: String, success: Boolean, errorMessage: String? = null) {
        val bundle = Bundle().apply {
            putString("backup_action_type", action)
            putBoolean("backup_success", success)
            // M15: jangan kirim pesan mentah (bisa memuat path absolut) — cukup kode kasar
            errorMessage?.let { putString("backup_error_code", backupErrorCode(it)) }
        }
        logEvent("backup_operation", bundle)
    }

    /** M15: ringkas pesan error backup jadi kode kasar tanpa path/info sensitif. */
    private fun backupErrorCode(message: String): String = when {
        message.contains("space", ignoreCase = true) || message.contains("ENOSPC", ignoreCase = true) -> "STORAGE"
        message.contains("permission", ignoreCase = true) || message.contains("EACCES", ignoreCase = true) -> "PERMISSION"
        message.contains("network", ignoreCase = true) || message.contains("timeout", ignoreCase = true) ||
            message.contains("connect", ignoreCase = true) -> "NETWORK"
        message.contains("transaction", ignoreCase = true) -> "DB_BUSY"
        message.contains("does not exist", ignoreCase = true) -> "DB_MISSING"
        else -> "OTHER"
    }
}
