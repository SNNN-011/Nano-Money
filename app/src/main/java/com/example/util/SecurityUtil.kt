package com.example.util

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SecurityUtil {
    private const val TAG = "SecurityUtil"

    /**
     * Detects dynamic instrumentation (e.g., Frida, Xposed) and active debuggers.
     * If detected, immediately terminates the application.
     */
    private fun checkDebuggingAndHooks() {
        try {
            // 1. Check TracerPid to detect active debugging
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                statusFile.forEachLine { line ->
                    if (line.startsWith("TracerPid:")) {
                        val pid = line.substringAfter("TracerPid:").trim().toIntOrNull()
                        if (pid != null && pid > 0) {
                            Log.e(TAG, "Debugger detected! TracerPid: $pid")
                            kotlin.system.exitProcess(0)
                        }
                    }
                }
            }

            // 2. Scan loaded memory maps for suspicious libraries (Anti-Frida/Xposed)
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                mapsFile.forEachLine { line ->
                    val lowerLine = line.lowercase()
                    if (lowerLine.contains("frida") || 
                        lowerLine.contains("xposed") ||
                        lowerLine.contains("edxposed") ||
                        lowerLine.contains("lsposed") ||
                        lowerLine.contains("substrate")) {
                        Log.e(TAG, "Hooking framework detected via memory map: $line")
                        kotlin.system.exitProcess(0) // Forcefully exit
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform security checks", e)
        }
    }

    enum class SecurityStatus {
        SAFE,
        ROOTED,
        EMULATOR_RELEASE
    }

    /**
     * Checks if the device is rooted by executing multiple complementary checks.
     */
    fun isDeviceRooted(): Boolean {
        return checkBuildTags() || checkSuBinaryPaths() || checkSuCommandExecution()
    }

    /**
     * Checks if the app is running on an emulator.
     */
    fun isEmulator(context: Context): Boolean {
        val finger = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""
        val hardware = Build.HARDWARE ?: ""
        val board = Build.BOARD ?: ""

        val isGeneric = finger.startsWith("generic") || finger.startsWith("unknown")
        val isGoogleSdk = model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for x86")
        val isGenymotion = manufacturer.contains("Genymotion")
        val isBrandDeviceGeneric = brand.startsWith("generic") && device.startsWith("generic")
        val isProductSdk = "google_sdk" == product || product.contains("sdk_gphone") || product.contains("emulator")
        val isHardwareGoldfishOrRanchu = hardware.contains("goldfish") || hardware.contains("ranchu")
        val isBoardGoldfish = board.contains("goldfish")

        return isGeneric || isGoogleSdk || isGenymotion || isBrandDeviceGeneric || isProductSdk || isHardwareGoldfishOrRanchu || isBoardGoldfish
    }

    /**
     * Determines whether the app should block execution based on security policies:
     * - If device is rooted, block access.
     * - If device is an emulator and this is a release build (not DEBUG), block access.
     */
    fun checkSecurityStatus(context: Context): SecurityStatus {
        checkDebuggingAndHooks()
        if (isDeviceRooted()) {
            Log.w(TAG, "Security Violation: Device is rooted.")
            return SecurityStatus.ROOTED
        }

        if (isEmulator(context)) {
            // Under normal developer conditions (DEBUG build), emulator is allowed.
            // On Release builds, emulator execution is strictly forbidden.
            if (!BuildConfig.DEBUG) {
                Log.w(TAG, "Security Violation: Running on emulator in release build.")
                return SecurityStatus.EMULATOR_RELEASE
            }
        }

        return SecurityStatus.SAFE
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkSuBinaryPaths(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    private fun checkSuCommandExecution(): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            if (reader.readLine() != null) {
                return true
            }
        } catch (t: Throwable) {
            // Su command not found or execution failed, which is good (safer)
        } finally {
            process?.destroy()
        }
        return false
    }
}
