package com.shibaprasadsahu.billingkit.api

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.shibaprasadsahu.billingkit.LogLevel
import com.shibaprasadsahu.billingkit.internal.BillingClientWrapper
import com.shibaprasadsahu.billingkit.internal.BillingLogger
import com.shibaprasadsahu.billingkit.internal.SubscriptionManagerImpl
import com.shibaprasadsahu.billingkit.model.PurchaseResult
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Main entry point for BillingKit
 * Thread-safe singleton pattern for easy access from anywhere
 *
 * Initialize once in Application.onCreate():
 * ```
 * BillingKit.initialize(context, listOf("premium_monthly"))
 * ```
 *
 * Then use anywhere:
 * ```
 * BillingKit.getInstance().subscribe(activity, "premium_monthly") { result -> }
 * ```
 */
class BillingKit private constructor(
    private val subscriptionManager: SubscriptionManager
) {

    // ===================================
    // Flow-based API (Reactive)
    // ===================================

    /**
     * Flow of all subscription products (both active and inactive)
     * Updates automatically when products are fetched or subscription status changes
     *
     * Example:
     * ```
     * BillingKit.getInstance().subscriptionsFlow.collect { subscriptions ->
     *     // Update UI
     * }
     * ```
     */
    val subscriptionsFlow: StateFlow<List<SubscriptionDetails>>
        get() = subscriptionManager.subscriptionsFlow

    /**
     * Flow of only active subscriptions
     * Updates automatically when subscription status changes
     *
     * Example:
     * ```
     * BillingKit.getInstance().activeSubscriptionsFlow.collect { activeSubscriptions ->
     *     // Update UI
     * }
     * ```
     */
    val activeSubscriptionsFlow: StateFlow<List<SubscriptionDetails>>
        get() = subscriptionManager.activeSubscriptionsFlow

    // ===================================
    // Listener-based API (Callbacks)
    // ===================================

    /**
     * Set a listener to observe subscription product updates
     *
     * @param listener The listener to receive subscription updates
     */
    fun setSubscriptionUpdateListener(listener: SubscriptionUpdateListener) =
        subscriptionManager.setSubscriptionUpdateListener(listener)

    /**
     * Remove the subscription update listener
     */
    fun removeSubscriptionUpdateListener() =
        subscriptionManager.removeSubscriptionUpdateListener()

    /**
     * Set a listener to observe purchase updates
     *
     * @param listener The listener to receive purchase updates
     */
    fun setPurchaseUpdateListener(listener: PurchaseUpdateListener) =
        subscriptionManager.setPurchaseUpdateListener(listener)

    /**
     * Remove the purchase update listener
     */
    fun removePurchaseUpdateListener() =
        subscriptionManager.removePurchaseUpdateListener()

    // ===================================
    // Fetch Products
    // ===================================

    /**
     * Fetch subscription products from Google Play (lifecycle-aware)
     * Automatically fetches on ON_START with smart debouncing and retry logic
     *
     * @param lifecycleOwner The lifecycle owner (Activity or Fragment)
     * @param callback Optional callback for immediate result
     */
    fun fetchProducts(
        lifecycleOwner: LifecycleOwner,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)? = null
    ) = subscriptionManager.fetchProducts(lifecycleOwner, callback)

    /**
     * Fetch subscription products manually
     *
     * @param forceRefresh If true, bypasses the debounce interval
     * @param callback Called when products are fetched or on error
     */
    fun fetchProducts(
        forceRefresh: Boolean = false,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)? = null
    ) = subscriptionManager.fetchProducts(forceRefresh, callback)

    /**
     * Get cached subscription products synchronously
     *
     * @return List of cached subscription details
     */
    fun getCachedProducts(): List<SubscriptionDetails> =
        subscriptionManager.getCachedProducts()

    // ===================================
    // Subscribe (Purchase)
    // ===================================

    /**
     * Subscribe to a product (simplified - only requires productId)
     * Automatically selects the first available base plan and offer
     *
     * @param activity The activity to launch the billing flow
     * @param productId The subscription product ID
     * @param callback Called when purchase completes
     */
    fun subscribe(
        activity: Activity,
        productId: String,
        callback: (PurchaseResult) -> Unit
    ) = subscriptionManager.subscribe(activity, productId, callback)

    /**
     * Subscribe to a product with specific base plan and offer
     *
     * @param activity The activity to launch the billing flow
     * @param productId The subscription product ID
     * @param basePlanId The base plan ID
     * @param offerId Optional offer ID for promotional offers
     * @param callback Called when purchase completes
     */
    fun subscribe(
        activity: Activity,
        productId: String,
        basePlanId: String,
        offerId: String? = null,
        callback: (PurchaseResult) -> Unit
    ) = subscriptionManager.subscribe(activity, productId, basePlanId, offerId, callback)

    // ===================================
    // Check Subscription Status
    // ===================================

    /**
     * Check if user has an active subscription for a specific product
     *
     * @param productId The subscription product ID
     * @param callback Called with true if subscription is active
     */
    fun hasActiveSubscription(
        productId: String,
        callback: (Boolean) -> Unit
    ) = subscriptionManager.hasActiveSubscription(productId, callback)

    /**
     * Check if user has any active subscription
     *
     * @param callback Called with true if any subscription is active
     */
    fun hasAnyActiveSubscription(callback: (Boolean) -> Unit) =
        subscriptionManager.hasAnyActiveSubscription(callback)

    /**
     * Get the current active subscription (if any)
     *
     * @return SubscriptionDetails of active subscription or null
     */
    fun getActiveSubscription(): SubscriptionDetails? =
        subscriptionManager.getActiveSubscription()

    /**
     * Builder for configuring BillingKit
     */
    class Builder(private val context: Context) {
        private val subscriptionIds = mutableListOf<String>()
        private var logLevel: LogLevel = LogLevel.NONE
        private var base64PublicKey: String? = null

        /**
         * Set subscription product IDs to track
         * @param productIds List of subscription product IDs from Google Play Console
         */
        fun setSubscriptionIds(productIds: List<String>) = apply {
            subscriptionIds.clear()
            subscriptionIds.addAll(productIds)
        }

        /**
         * Add a single subscription product ID
         * @param productId The subscription product ID from Google Play Console
         */
        fun addSubscriptionId(productId: String) = apply {
            subscriptionIds.add(productId)
        }

        /**
         * Configure logging level
         * @param level The minimum log level to output (VERBOSE, DEBUG, INFO, WARN, ERROR, NONE)
         *
         * Example:
         * ```
         * .setLogLevel(LogLevel.DEBUG)  // Show DEBUG and above (DEBUG, INFO, WARN, ERROR)
         * .setLogLevel(LogLevel.ERROR)  // Show only ERROR logs
         * .setLogLevel(LogLevel.NONE)   // Disable all logging (default)
         * ```
         */
        fun setLogLevel(level: LogLevel) = apply {
            this.logLevel = level
        }

        /**
         * Set Base64-encoded public key for purchase signature verification (OPTIONAL but RECOMMENDED)
         *
         * Get your key from: Google Play Console > Your App > Monetization setup > Licensing
         *
         * If not set, purchases will NOT be verified for authenticity.
         * Setting this key adds protection against purchase tampering and fraud.
         *
         * @param key Base64-encoded RSA public key from Google Play Console
         *
         * Example:
         * ```
         * .setBase64PublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
         * ```
         */
        fun setBase64PublicKey(key: String?) = apply {
            this.base64PublicKey = key
        }

        /**
         * Build the BillingKit instance
         */
        fun build(): BillingKit {
            require(subscriptionIds.isNotEmpty()) {
                "At least one subscription ID must be provided"
            }

            // Configure logger
            BillingLogger.configure(logLevel)

            val billingClient = BillingClientWrapper(
                context = context.applicationContext,
                base64PublicKey = base64PublicKey
            )
            val subscriptionManager = SubscriptionManagerImpl(
                billingClient = billingClient,
                subscriptionIds = subscriptionIds
            )

            return BillingKit(subscriptionManager)
        }
    }

    companion object {
        @Volatile
        private var instance: BillingKit? = null
        private val lock = ReentrantLock()

        /**
         * Get the singleton instance of BillingKit
         * Must call initialize() first in Application.onCreate()
         *
         * @throws IllegalStateException if BillingKit is not initialized
         */
        @JvmStatic
        fun getInstance(): BillingKit {
            return instance ?: throw IllegalStateException(
                "BillingKit is not initialized. Call BillingKit.initialize(context) in Application.onCreate() first."
            )
        }

        /**
         * Check if BillingKit is initialized
         */
        @JvmStatic
        fun isInitialized(): Boolean = instance != null

        /**
         * Initialize BillingKit singleton
         * Call this in Application.onCreate()
         *
         * Thread-safe with double-checked locking to prevent race conditions
         *
         * @param context Application context
         * @param subscriptionIds List of subscription product IDs from Google Play Console
         * @param logLevel Log level for debugging (default: NONE)
         * @param base64PublicKey Optional Base64-encoded public key for signature verification
         * @throws IllegalStateException if already initialized
         */
        @JvmStatic
        @JvmOverloads
        fun initialize(
            context: Context,
            subscriptionIds: List<String>,
            logLevel: LogLevel = LogLevel.NONE,
            base64PublicKey: String? = null
        ) {
            // Double-checked locking for thread safety
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        require(subscriptionIds.isNotEmpty()) {
                            "At least one subscription ID must be provided"
                        }

                        // Configure logger
                        BillingLogger.configure(logLevel)

                        val billingClient = BillingClientWrapper(
                            context = context.applicationContext,
                            base64PublicKey = base64PublicKey
                        )
                        val subscriptionManager = SubscriptionManagerImpl(
                            billingClient = billingClient,
                            subscriptionIds = subscriptionIds
                        )

                        instance = BillingKit(subscriptionManager)
                    }
                }
            }
        }

        /**
         * Initialize BillingKit singleton with builder pattern
         * Call this in Application.onCreate()
         *
         * @param block Builder configuration block
         */
        @JvmStatic
        fun initialize(context: Context, block: Builder.() -> Unit) {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = builder(context).apply(block).build()
                    }
                }
            }
        }

        /**
         * Create a new BillingKit builder (for DI frameworks like Hilt)
         * @param context Application or Activity context
         */
        @JvmStatic
        fun builder(context: Context) = Builder(context)

        /**
         * Destroy the singleton instance
         * Only use this for testing or when you need to reinitialize
         */
        @JvmStatic
        internal fun destroy() {
            lock.withLock {
                instance = null
            }
        }
    }
}
