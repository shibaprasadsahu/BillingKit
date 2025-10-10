package com.shibaprasadsahu.billingkit.internal

import android.util.Log
import com.shibaprasadsahu.billingkit.LogLevel

/**
 * Internal logger for BillingKit with log level support
 *
 * Note: This object and its methods are kept by ProGuard rules to ensure
 * logging works correctly in release builds when enabled.
 */
internal object BillingLogger {
    private const val TAG = "BillingKit"

    @Volatile
    private var minLevel: LogLevel = LogLevel.NONE

    @JvmStatic
    fun configure(logLevel: LogLevel) {
        minLevel = logLevel
        // Log configuration change to verify logger is working
        if (logLevel != LogLevel.NONE) {
            Log.i(TAG, "BillingKit logger configured: $logLevel")
        }
    }

    @JvmStatic
    fun verbose(message: String) {
        if (shouldLog(LogLevel.VERBOSE)) {
            Log.v(TAG, message)
        }
    }

    @JvmStatic
    fun debug(message: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            Log.d(TAG, message)
        }
    }

    @JvmStatic
    fun info(message: String) {
        if (shouldLog(LogLevel.INFO)) {
            Log.i(TAG, message)
        }
    }

    @JvmStatic
    fun warn(message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.WARN)) {
            if (throwable != null) {
                Log.w(TAG, message, throwable)
            } else {
                Log.w(TAG, message)
            }
        }
    }

    @JvmStatic
    fun error(message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    @JvmStatic
    private fun shouldLog(level: LogLevel): Boolean {
        if (minLevel == LogLevel.NONE) return false
        return level.ordinal >= minLevel.ordinal
    }

    // Deprecated methods for backward compatibility (internal use only)
    @Deprecated("Use configure(LogLevel) instead", ReplaceWith("configure(if (enabled) LogLevel.DEBUG else LogLevel.NONE)"))
    fun enable(enabled: Boolean) {
        configure(if (enabled) LogLevel.DEBUG else LogLevel.NONE)
    }

    @Deprecated("Use debug() instead", ReplaceWith("debug(message)"))
    fun d(message: String) = debug(message)

    @Deprecated("Use error() instead", ReplaceWith("error(message, throwable)"))
    fun e(message: String, throwable: Throwable? = null) = error(message, throwable)

    @Deprecated("Use warn() instead", ReplaceWith("warn(message)"))
    fun w(message: String) = warn(message)

    @Deprecated("Use info() instead", ReplaceWith("info(message)"))
    fun i(message: String) = info(message)
}
