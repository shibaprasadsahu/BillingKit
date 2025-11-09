package com.shibaprasadsahu.billingkit.api

import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.Purchase

/**
 * Listener for purchase updates
 * Called when purchases are updated (new purchase, renewal, etc.)
 * Returns list of all active purchases along with the lifecycle owner
 * Automatically cleaned up when lifecycle is destroyed
 */
fun interface PurchaseUpdateListener {
    /**
     * Called when purchases are updated
     * @param lifecycleOwner The lifecycle owner (Activity/Fragment) for lifecycle-aware operations
     * @param purchases List of active purchases (empty list if no active purchases)
     */
    fun onPurchasesUpdated(lifecycleOwner: LifecycleOwner, purchases: List<Purchase>)
}
