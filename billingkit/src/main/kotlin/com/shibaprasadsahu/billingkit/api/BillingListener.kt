package com.shibaprasadsahu.billingkit.api

import com.android.billingclient.api.Purchase
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails

/**
 * Listener for subscription product updates
 * Called when subscription products are fetched or updated
 * Returns list of all subscriptions (empty list if error or no products)
 */
fun interface SubscriptionUpdateListener {
    /**
     * Called when subscriptions are updated
     * @param subscriptions List of available subscription products with all details
     *                      Empty list if error occurred or no products available
     */
    fun onSubscriptionsUpdated(subscriptions: List<SubscriptionDetails>)
}

/**
 * Listener for purchase updates
 * Called when purchases are updated (new purchase, renewal, etc.)
 * Returns list of all active purchases
 */
fun interface PurchaseUpdateListener {
    /**
     * Called when purchases are updated
     * @param purchases List of active purchases
     *                  Empty list if no active purchases
     */
    fun onPurchasesUpdated(purchases: List<Purchase>)
}
