package com.example.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RemoteConfigHelper {
    private const val TAG = "RemoteConfigHelper"

    private val _welcomeMessage = MutableStateFlow("Selamat Datang!")
    val welcomeMessage: StateFlow<String> = _welcomeMessage.asStateFlow()

    private val _welcomeMessageColor = MutableStateFlow("#4682B4") // SteelBlue default
    val welcomeMessageColor: StateFlow<String> = _welcomeMessageColor.asStateFlow()

    private val _showNewFeature = MutableStateFlow(false)
    val showNewFeature: StateFlow<Boolean> = _showNewFeature.asStateFlow()

    private val _enableTelemetry = MutableStateFlow(true)
    val enableTelemetry: StateFlow<Boolean> = _enableTelemetry.asStateFlow()

    private val _preventScreenshot = MutableStateFlow(true)
    val preventScreenshot: StateFlow<Boolean> = _preventScreenshot.asStateFlow()

    fun initAndFetchConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        
        val cacheExpiration = if (com.example.BuildConfig.DEBUG) 0L else 3600L
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(cacheExpiration)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaultValues = mapOf(
            "welcome_message" to "Selamat Datang!",
            "welcome_message_color" to "#4682B4",
            "show_new_feature" to false,
            "enable_telemetry" to true,
            "prevent_screenshot" to true
        )
        remoteConfig.setDefaultsAsync(defaultValues)

        fetchConfig(remoteConfig)

        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate : ConfigUpdate) {
                Log.d(TAG, "Konfigurasi diperbarui: ${configUpdate.updatedKeys}")
                if (configUpdate.updatedKeys.contains("welcome_message") || 
                    configUpdate.updatedKeys.contains("welcome_message_color") || 
                    configUpdate.updatedKeys.contains("show_new_feature") ||
                    configUpdate.updatedKeys.contains("enable_telemetry") ||
                    configUpdate.updatedKeys.contains("prevent_screenshot")) {
                    remoteConfig.activate().addOnCompleteListener { 
                        updateValues(remoteConfig)
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "Gagal mendengarkan update konfigurasi.", error)
            }
        })
    }

    private fun fetchConfig(remoteConfig: FirebaseRemoteConfig) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Konfigurasi berhasil diambil. Updated: $updated")
                    updateValues(remoteConfig)
                } else {
                    Log.d(TAG, "Gagal mengambil konfigurasi")
                }
            }
    }

    private fun updateValues(remoteConfig: FirebaseRemoteConfig) {
        _welcomeMessage.value = remoteConfig.getString("welcome_message")
        
        val colorParam = remoteConfig.getString("welcome_message_color")
        if (colorParam.isNotEmpty()) {
            _welcomeMessageColor.value = colorParam
        }
        
        _showNewFeature.value = remoteConfig.getBoolean("show_new_feature")

        val telemetryEnabled = remoteConfig.getBoolean("enable_telemetry")
        _enableTelemetry.value = telemetryEnabled
        TelemetryHelper.setTelemetryEnabled(telemetryEnabled)

        _preventScreenshot.value = remoteConfig.getBoolean("prevent_screenshot")
    }
}
