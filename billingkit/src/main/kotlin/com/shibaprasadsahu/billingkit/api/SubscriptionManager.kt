package com.shibaprasadsahu.billingkit.api

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import com.shibaprasadsahu.billingkit.model.PurchaseResult
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails
import kotlinx.coroutines.flow.StateFlow

/**
 * Public interface for managing subscriptions
 */
interface SubscriptionManager {

    /**
     * Flow of all subscription products (both active and inactive)
     * Updates automatically when products are fetched or subscription status changes
     */
    val subscriptionsFlow: StateFlow<List<SubscriptionDetails>>

    /**
     * Set a listener to observe purchase updates with lifecycle awareness
     * Automatically queries purchases when activity resumes
     *
     * @param lifecycleOwner The lifecycle owner (Activity or Fragment)
     * @param listener The listener to receive purchase updates (always called, even with empty list)
     */
    fun setPurchaseUpdateListener(lifecycleOwner: LifecycleOwner, listener: PurchaseUpdateListener)

    /**
     * Remove the purchase update listener
     */
    fun removePurchaseUpdateListener()

    /**
     * Fetch subscription products from Google Play
     * This method is lifecycle-aware and will automatically fetch on ON_START
     * It includes smart debouncing (minimum 3 minutes between fetches) and retry logic
     *
     * @param lifecycleOwner The lifecycle owner (Activity or Fragment)
     * @param callback Optional callback for immediate result (in addition to listener)
     */
    fun fetchProducts(
        lifecycleOwner: LifecycleOwner,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)? = null
    )

    /**
     * Fetch subscription products manually without lifecycle
     * Includes smart debouncing and retry logic
     *
     * @param forceRefresh If true, bypasses the debounce interval and fetches immediately
     * @param callback Called when products are fetched or on error
     */
    fun fetchProducts(
        forceRefresh: Boolean = false,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)? = null
    )

    /**
     * Get cached subscription products synchronously
     * Returns the last fetched list or empty list if not fetched yet
     *
     * @return List of cached subscription details
     */
    fun getCachedProducts(): List<SubscriptionDetails>

    /**
     * Purchase or subscribe to a product (simplified - only requires productId)
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
    )

    /**
     * Purchase or subscribe to a product with specific base plan and offer
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
    )
}
