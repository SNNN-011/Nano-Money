package com.example.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryHelper {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var firebaseCrashlytics: FirebaseCrashlytics? = null

    fun initialize(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseCrashlytics = FirebaseCrashlytics.getInstance()
            
            val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            firebaseCrashlytics?.setCrashlyticsCollectionEnabled(true)
            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
            
            logCrashlytics("TelemetryHelper initialized successfully. IsDebug: $isDebug")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
            firebaseCrashlytics?.setCrashlyticsCollectionEnabled(enabled)
            logCrashlytics("Telemetry dynamic status changed: enabled=$enabled")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            firebaseAnalytics?.logEvent(eventName, params)
            
            params?.let { bundle ->
                val keys = bundle.keySet()
                val paramPairs = keys.map { "$it=${bundle.get(it)}" }.joinToString(", ")
                logCrashlytics("Event Logged: $eventName { $paramPairs }")
            } ?: run {
                logCrashlytics("Event Logged: $eventName")
            }
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
            firebaseAnalytics?.setUserId(userId)
            firebaseCrashlytics?.setUserId(userId)
        } catch (e: Exception) {
            logNonFatal(e, "Gagal menset userId")
        }
    }

    fun trackScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun trackTransactionAction(action: String, type: String, category: String, amount: Double) {
        val amountTier = if (amount < 100000.0) "KECIL" else "BESAR"
        val bundle = Bundle().apply {
            putString("transaction_action", action)
            putString("transaction_type", type)
            putString("transaction_category", category)
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
            errorMessage?.let { putString("backup_error", it) }
        }
        logEvent("backup_operation", bundle)
    }
}
