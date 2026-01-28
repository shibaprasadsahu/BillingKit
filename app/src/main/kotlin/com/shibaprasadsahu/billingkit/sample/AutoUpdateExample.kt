package com.shibaprasadsahu.billingkit.sample

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.shibaprasadsahu.billingkit.api.BillingKit
import kotlinx.coroutines.launch

/**
 * Example showing how subscription updates in one place automatically
 * update in ALL places observing the Flow
 *
 * Scenario:
 * 1. MainActivity observes productsFlow
 * 2. User navigates to SettingsActivity
 * 3. User subscribes in SettingsActivity
 * 4. MainActivity automatically updates (even though it's in background)
 * 5. When user returns to MainActivity, it shows updated status
 */

// ============================================
// MainActivity - Observes and auto-updates
// ============================================
class MainActivityExample : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val billingKit = BillingKit.getInstance()

        // Observe products - auto-updates when ANYTHING changes
        lifecycleScope.launch {
            billingKit.productsFlow.collect { products ->
                // This gets called:
                // 1. When fetch completes
                // 2. When purchase happens (even in another Activity!)
                // 3. When subscription status changes
                println("MainActivity: Products updated - ${products.size} items")
                updateUI(products)
            }
        }

        // Optional: Listen to purchases updates with lifecycle awareness
        // Automatically queries purchases on activity resume
        // Automatically cleans up when activity is destroyed
        billingKit.setPurchaseUpdateListener(this) { owner, purchases ->
            println("MainActivity: Purchases updated - ${purchases.size} active")
            // owner is LifecycleOwner (Activity) for lifecycle-aware operations
            // purchases is List<Purchase> - all active purchases
            // Always called, even with empty list
        }

        // Products are automatically fetched when billing connection is established!
        // No need to call fetchProducts() - it happens automatically on initialize ✨

        // Optional: Lifecycle-aware refresh (fetches on ON_START with debouncing)
        // billingKit.fetchProducts(this)
    }

    private fun updateUI(products: List<com.shibaprasadsahu.billingkit.model.SubscriptionDetails>) {
        // Your UI update logic
    }
}

// ============================================
// SettingsActivity - Subscribes to product
// ============================================
class SettingsActivityExample : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val billingKit = BillingKit.getInstance()

        // Also observe active subscriptions (auto-filtered)
        lifecycleScope.launch {
            billingKit.activeSubscriptionsFlow.collect { activeSubscriptions ->
                println("SettingsActivity: Active subscriptions - ${activeSubscriptions.size}")
                updateSettingsUI(activeSubscriptions)
            }
        }

        // Subscribe button click
        // When user subscribes here, MainActivity automatically updates!
        subscribeButton()
    }

    private fun subscribeButton() {
        val billingKit = BillingKit.getInstance()

        // Simple subscribe
        billingKit.subscribe(this, "premium_monthly") { result ->
            when (result) {
                is com.shibaprasadsahu.billingkit.model.PurchaseResult.Success -> {
                    // Purchase successful
                    // MainActivity's Flow will automatically update!
                    // SettingsActivity's Flow will automatically update!
                    println("Purchase successful - all observers notified automatically")
                }
                else -> {}
            }
        }
    }

    private fun updateSettingsUI(activeSubscriptions: List<com.shibaprasadsahu.billingkit.model.SubscriptionDetails>) {
        // Your settings UI update
    }
}

// ============================================
// ViewModel approach - Shared across screens
// ============================================
class SubscriptionViewModel {

    private val billingKit = BillingKit.getInstance()

    // Expose flows from BillingKit
    val products = billingKit.productsFlow
    val activeSubscriptions = billingKit.activeSubscriptionsFlow

    // Fetch products
    fun fetchProducts(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        billingKit.fetchProducts(lifecycleOwner)
    }

    // Subscribe
    fun subscribe(activity: Activity, productId: String, callback: (com.shibaprasadsahu.billingkit.model.PurchaseResult) -> Unit) {
        billingKit.subscribe(activity, productId, callback)
    }
}

// ============================================
// Using ViewModel in Activities
// ============================================
class MainActivityWithViewModel : ComponentActivity() {

    private lateinit var viewModel: SubscriptionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = SubscriptionViewModel()

        // Observe products from ViewModel
        lifecycleScope.launch {
            viewModel.products.collect { products ->
                // Auto-updates when ANY activity makes a purchase
                println("MainActivity (ViewModel): ${products.size} products")
            }
        }

        // Fetch once
        viewModel.fetchProducts(this)
    }
}

class SettingsActivityWithViewModel : ComponentActivity() {

    private lateinit var viewModel: SubscriptionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = SubscriptionViewModel()

        // Observe active subscriptions from ViewModel
        lifecycleScope.launch {
            viewModel.activeSubscriptions.collect { activeSubscriptions ->
                // Auto-updates when status changes
                println("SettingsActivity (ViewModel): ${activeSubscriptions.size} active")
            }
        }

        // Subscribe
        viewModel.subscribe(this, "premium_monthly") { result ->
            // MainActivity automatically updates via Flow!
        }
    }
}

// ============================================
// Real-world example: Purchase in one screen,
// update everywhere
// ============================================
class RealWorldExample {

    /**
     * SCENARIO:
     *
     * 1. User opens MainActivity
     *    - Observes productsFlow
     *    - Shows "No subscription"
     *
     * 2. User navigates to PaywallActivity
     *    - Also observes productsFlow
     *    - User clicks "Subscribe"
     *
     * 3. User completes purchase in PaywallActivity
     *    - Purchase successful
     *    - Flow automatically updates
     *
     * 4. PaywallActivity UI updates to "Subscribed ✓"
     *    - MainActivity ALSO updates (even in background!)
     *
     * 5. User presses back to MainActivity
     *    - Already shows "Active Subscription ✓"
     *    - No need to refresh or fetch again!
     *
     *
     * HOW IT WORKS:
     *
     * - BillingKit uses StateFlow (hot stream)
     * - All collectors receive the same data
     * - When subscribe() succeeds, it calls fetchProducts(forceRefresh = true)
     * - This updates the StateFlow
     * - ALL collectors (MainActivity, PaywallActivity, etc.) get notified
     * - UI updates automatically everywhere!
     *
     *
     * CODE EXAMPLE:
     */

    // MainActivity
    class MainScreen : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val billingKit = BillingKit.getInstance()

            lifecycleScope.launch {
                billingKit.activeSubscriptionsFlow.collect { active ->
                    // This updates AUTOMATICALLY when user subscribes
                    // in PaywallActivity!
                    if (active.isEmpty()) {
                        showNoSubscriptionBanner()
                    } else {
                        showSubscribedStatus(active.first())
                    }
                }
            }

            billingKit.fetchProducts(this)
        }

        private fun showNoSubscriptionBanner() {
            // Show "Subscribe to Premium" banner
        }

        private fun showSubscribedStatus(subscription: com.shibaprasadsahu.billingkit.model.SubscriptionDetails) {
            // Show "Premium Active ✓" status
        }
    }

    // PaywallActivity
    class PaywallScreen : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val billingKit = BillingKit.getInstance()

            lifecycleScope.launch {
                billingKit.productsFlow.collect { products ->
                    // Show subscription options
                    displaySubscriptionOptions(products)
                }
            }

            billingKit.fetchProducts(this)
        }

        private fun displaySubscriptionOptions(subscriptions: List<com.shibaprasadsahu.billingkit.model.SubscriptionDetails>) {
            // Show paywall UI
        }

        private fun onSubscribeButtonClick() {
            val billingKit = BillingKit.getInstance()

            billingKit.subscribe(this, "premium_monthly") { result ->
                when (result) {
                    is com.shibaprasadsahu.billingkit.model.PurchaseResult.Success -> {
                        // SUCCESS!
                        // 1. PaywallActivity updates (via Flow)
                        // 2. MainActivity updates (via Flow)
                        // 3. Any other observer updates (via Flow)
                        // ALL AUTOMATIC!

                        // Just close paywall
                        finish()
                    }
                    else -> {}
                }
            }
        }
    }
}

// ============================================
// Summary: Key Points
// ============================================
/**
 * KEY POINTS:
 *
 * 1. OBSERVE PRODUCTS (Flow-based):
 *    - lifecycleScope.launch { billingKit.productsFlow.collect { products -> } }
 *    - Auto-updates when products details vary
 *
 * 2. OBSERVE ACTIVE SUBSCRIPTIONS (Flow-based):
 *    - lifecycleScope.launch { billingKit.activeSubscriptionsFlow.collect { activeSubs -> } }
 *    - Only contains currently active subscriptions
 *
 * 3. OBSERVE PURCHASES (Listener-based):
 *    - billingKit.setPurchaseUpdateListener(lifecycleOwner) { owner, purchases -> }
 *    - Receives LifecycleOwner and List<Purchase>
 *    - Always called, even with empty list (no active purchases)
 *    - Automatically queries purchases on activity resume
 *    - Automatically cleans up when activity is destroyed
 *    - Just ONE method, no error callback needed!
 *
 * 3. FETCH WITH LIFECYCLE:
 *    - billingKit.fetchProducts(this) // 'this' is LifecycleOwner
 *    - Automatically fetches products on ON_START
 *    - Smart debouncing (won't spam requests)
 *
 * 4. SUBSCRIBE ANYWHERE:
 *    - billingKit.subscribe(activity, productId) { }
 *    - When successful, ALL observers update automatically
 *
 * 5. NO MANUAL UPDATES NEEDED:
 *    - Don't manually call fetch after subscribe
 *    - Don't manually update UI in multiple places
 *    - Flow/Listeners handle everything!
 *
 * 6. LIFECYCLE AWARE:
 *    - Collection stops when screen is destroyed
 *    - Restarts when screen is recreated
 *    - No memory leaks!
 */
