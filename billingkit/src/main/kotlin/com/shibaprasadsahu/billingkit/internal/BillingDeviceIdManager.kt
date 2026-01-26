package com.shibaprasadsahu.billingkit.internal

import android.content.Context
import com.shibaprasadsahu.billingkit.LogLevel
import com.shibaprasadsahu.persistid.BackupStrategy
import com.shibaprasadsahu.persistid.PersistId
import com.shibaprasadsahu.persistid.PersistIdConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Internal manager for accessing the persistent unique device identifier.
 * Wraps PersistID library to provide device ID management within BillingKit.
 */
import androidx.lifecycle.ProcessLifecycleOwner
import com.shibaprasadsahu.persistid.PersistIdCallback
import kotlinx.coroutines.CompletableDeferred

/**
 * Internal manager for accessing the persistent unique device identifier.
 * Wraps PersistID library to provide device ID management within BillingKit.
 */
internal class BillingDeviceIdManager(
    private val context: Context,
    logLevel: LogLevel
) {
    private var cachedDeviceId: String = ""
    private val deviceIdDeferred = CompletableDeferred<String>()

    init {
        initPersistId(logLevel)
        observePersistId()
    }

    private fun initPersistId(logLevel: LogLevel) {
        try {
            // Map BillingKit LogLevel to PersistID LogLevel
            val persistIdLogLevel = when (logLevel) {
                LogLevel.VERBOSE, LogLevel.DEBUG -> com.shibaprasadsahu.persistid.LogLevel.DEBUG
                LogLevel.INFO -> com.shibaprasadsahu.persistid.LogLevel.INFO
                LogLevel.WARN -> com.shibaprasadsahu.persistid.LogLevel.WARN
                LogLevel.ERROR -> com.shibaprasadsahu.persistid.LogLevel.ERROR
                LogLevel.NONE -> com.shibaprasadsahu.persistid.LogLevel.NONE
            }

            val config = PersistIdConfig.Builder()
                .setBackupStrategy(BackupStrategy.BLOCK_STORE)
                .setBackgroundSync(true)
                .setLogLevel(persistIdLogLevel)
                .build()
            
            PersistId.initialize(context, config)
            BillingLogger.debug("PersistID initialized within BillingKit")
        } catch (e: Exception) {
            BillingLogger.error("Failed to initialize PersistID in BillingKit: ${e.message}", e)
        }
    }

    private fun observePersistId() {
        try {
            if (PersistId.isInitialized()) {
                // Use ProcessLifecycleOwner to observe globally (app lifecycle)
                val lifecycleOwner = ProcessLifecycleOwner.get()
                
                // We need to run this on main thread as observe likely requires it
                // Using a simple post to main looper
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                         PersistId.getInstance().observe(lifecycleOwner, object : PersistIdCallback {
                            override fun onReady(identifier: String) {
                                BillingLogger.debug("Device ID ready: $identifier")
                                cachedDeviceId = identifier
                                if (!deviceIdDeferred.isCompleted) {
                                    deviceIdDeferred.complete(identifier)
                                }
                            }

                            override fun onError(error: Exception) {
                                BillingLogger.error("PersistID error in BillingKit: ${error.message}", error)
                                if (!deviceIdDeferred.isCompleted) {
                                    // Don't fail the deferred, just complete with empty or keep waiting?
                                    // Let's not block indefinitely if error occurs
                                    // deviceIdDeferred.complete("") 
                                    // Actually, maybe retry? For now let's just log.
                                }
                            }
                        })
                    } catch (e: Exception) {
                        BillingLogger.error("Error observing PersistID: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            BillingLogger.error("Error setting up PersistID observer: ${e.message}", e)
        }
    }

    /**
     * Gets the current device ID.
     * Suspends until the ID is initialized.
     */
    suspend fun getDeviceId(): String {
        if (cachedDeviceId.isNotEmpty()) {
            return cachedDeviceId
        }
        
        // Wait for ID to be ready (with timeout maybe? 5 seconds)
        return try {
             // Basic wait
             deviceIdDeferred.await()
        } catch (e: Exception) {
            BillingLogger.error("Error awaiting device ID: ${e.message}", e)
            ""
        }
    }
}
