package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Drop-in replacement for android.util.Log that suppresses all output in release builds.
 * Prevents accidental leakage of sensitive data (tokens, keys, user info) via logcat.
 */
object SecureLog {
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }
}
