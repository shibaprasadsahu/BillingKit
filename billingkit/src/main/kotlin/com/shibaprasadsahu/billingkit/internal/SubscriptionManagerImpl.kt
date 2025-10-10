package com.shibaprasadsahu.billingkit.internal

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.Purchase
import com.shibaprasadsahu.billingkit.api.PurchaseUpdateListener
import com.shibaprasadsahu.billingkit.api.SubscriptionManager
import com.shibaprasadsahu.billingkit.api.SubscriptionUpdateListener
import com.shibaprasadsahu.billingkit.model.PricingPhase
import com.shibaprasadsahu.billingkit.model.PricingPhaseType
import com.shibaprasadsahu.billingkit.model.PurchaseResult
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal implementation of SubscriptionManager
 */
internal class SubscriptionManagerImpl(
    private val billingClient: BillingClientWrapper,
    private val subscriptionIds: List<String>
) : SubscriptionManager {

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    init {
        // Set up callback to auto-fetch when billing connection is established
        billingClient.setOnConnectionEstablishedCallback {
            BillingLogger.info("Billing connection established - auto-fetching purchases and subscriptions")
            scope.launch {
                try {
                    // Fetch everything (purchases + products) in one call
                    // This prevents duplicate purchase queries
                    fetchProducts(forceRefresh = true)
                } catch (e: Exception) {
                    BillingLogger.error("Error during initial fetch on connection: ${e.message}", e)
                }
            }
        }
    }

    // Listeners
    private var subscriptionUpdateListener: SubscriptionUpdateListener? = null
    private var purchaseUpdateListener: PurchaseUpdateListener? = null

    // StateFlows for reactive programming
    private val _subscriptionsFlow = MutableStateFlow<List<SubscriptionDetails>>(emptyList())
    override val subscriptionsFlow: StateFlow<List<SubscriptionDetails>> =
        _subscriptionsFlow.asStateFlow()

    private val _activeSubscriptionsFlow = MutableStateFlow<List<SubscriptionDetails>>(emptyList())
    override val activeSubscriptionsFlow: StateFlow<List<SubscriptionDetails>> =
        _activeSubscriptionsFlow.asStateFlow()

    // Caching and state management - lazy initialization for better performance
    private val cachedSubscriptions by lazy { mutableListOf<SubscriptionDetails>() }
    private val activePurchases by lazy { mutableMapOf<String, Purchase>() }

    // Fetch management
    private var lastFetchTimestamp: Long = 0
    private val fetchMutex by lazy { Mutex() }
    private val isFetching by lazy { AtomicBoolean(false) }
    private val FETCH_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes
    private val MAX_RETRY_ATTEMPTS = 3
    private val RETRY_DELAY_MS = 3000L // 3 seconds base delay

    override fun setSubscriptionUpdateListener(listener: SubscriptionUpdateListener) {
        subscriptionUpdateListener = listener
        // Immediately deliver cached data if available
        if (cachedSubscriptions.isNotEmpty()) {
            scope.launch(Dispatchers.Main) {
                listener.onSubscriptionsUpdated(cachedSubscriptions.toList())
            }
        }
    }

    override fun removeSubscriptionUpdateListener() {
        subscriptionUpdateListener = null
    }

    override fun setPurchaseUpdateListener(listener: PurchaseUpdateListener) {
        purchaseUpdateListener = listener
    }

    override fun removePurchaseUpdateListener() {
        purchaseUpdateListener = null
    }

    override fun fetchProducts(
        lifecycleOwner: LifecycleOwner,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)?
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // Fetch on ON_START (not ON_RESUME to avoid over-fetching)
                fetchProducts(forceRefresh = false, callback = callback)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Fetch immediately if already started
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            fetchProducts(forceRefresh = false, callback = callback)
        }
    }

    override fun fetchProducts(
        forceRefresh: Boolean,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)?
    ) {
        scope.launch {
            try {
                // Check debouncing - avoid fetching if within interval
                if (!forceRefresh) {
                    val timeSinceLastFetch = System.currentTimeMillis() - lastFetchTimestamp
                    if (timeSinceLastFetch < FETCH_INTERVAL_MS) {
                        BillingLogger.debug("Skipping fetch - within debounce interval (${timeSinceLastFetch}ms < ${FETCH_INTERVAL_MS}ms)")
                        // Return cached data if available
                        if (cachedSubscriptions.isNotEmpty()) {
                            callback?.let {
                                withContext(Dispatchers.Main) {
                                    it(Result.success(cachedSubscriptions.toList()))
                                }
                            }
                        }
                        return@launch
                    }
                }

                // Check if already fetching (race condition protection)
                if (isFetching.get()) {
                    BillingLogger.debug("Fetch already in progress, skipping duplicate request")
                    // Return cached data if available
                    if (cachedSubscriptions.isNotEmpty()) {
                        callback?.let {
                            withContext(Dispatchers.Main) {
                                it(Result.success(cachedSubscriptions.toList()))
                            }
                        }
                    }
                    return@launch
                }

                // Perform fetch with retry logic
                fetchWithRetry(callback)

            } catch (e: Exception) {
                BillingLogger.error("Unexpected error in fetchProducts: ${e.message}", e)
                handleFetchError(e, callback)
            }
        }
    }

    private suspend fun fetchWithRetry(
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)?
    ) {
        var attempt = 0

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++

            try {
                fetchMutex.withLock {
                    if (isFetching.compareAndSet(false, true)) {
                        try {
                            BillingLogger.debug("Fetching subscription products (attempt $attempt/$MAX_RETRY_ATTEMPTS)")

                            // Query product details
                            val productDetailsList =
                                billingClient.querySubscriptionDetailsById(subscriptionIds)

                            if (productDetailsList.isEmpty()) {
                                BillingLogger.warn("No products found for subscription IDs: $subscriptionIds")
                            }

                            // Query active purchases
                            val purchases = billingClient.queryPurchases()
                            updateActivePurchases(purchases)

                            // Map product details to SubscriptionDetails
                            val subscriptionDetailsList =
                                productDetailsList.flatMap { productDetails ->
                                    productDetails.subscriptionOfferDetails?.map { offer ->
                                        createSubscriptionDetails(productDetails, offer)
                                    } ?: emptyList()
                                }

                            // Update cache
                            cachedSubscriptions.clear()
                            cachedSubscriptions.addAll(subscriptionDetailsList)

                            // Update timestamp
                            lastFetchTimestamp = System.currentTimeMillis()

                            BillingLogger.info("Successfully fetched ${subscriptionDetailsList.size} subscription offers")

                            // Notify listeners
                            notifySubscriptionUpdate(subscriptionDetailsList)

                            // Invoke callback
                            callback?.let {
                                withContext(Dispatchers.Main) {
                                    it(Result.success(subscriptionDetailsList))
                                }
                            }

                            return // Success, exit retry loop

                        } finally {
                            isFetching.set(false)
                        }
                    }
                }
            } catch (e: Exception) {
                BillingLogger.warn("Fetch attempt $attempt failed: ${e.message}")

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(RETRY_DELAY_MS * attempt) // Exponential backoff
                } else {
                    // Max retries reached
                    BillingLogger.error("Max retry attempts reached for fetching products", e)
                    handleFetchError(e, callback)
                }
            }
        }
    }

    private fun createSubscriptionDetails(
        productDetails: com.android.billingclient.api.ProductDetails,
        offer: com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
    ): SubscriptionDetails {
        val pricingPhases = offer.pricingPhases.pricingPhaseList.mapIndexed { index, phase ->
            val phaseType = when {
                phase.priceAmountMicros == 0L -> PricingPhaseType.FREE_TRIAL
                index == 0 && phase.priceAmountMicros > 0 -> PricingPhaseType.INTRODUCTORY
                else -> PricingPhaseType.REGULAR
            }

            PricingPhase(
                formattedPrice = phase.formattedPrice,
                priceAmountMicros = phase.priceAmountMicros,
                priceCurrencyCode = phase.priceCurrencyCode,
                billingPeriod = phase.billingPeriod,
                billingCycleCount = phase.billingCycleCount,
                recurrenceMode = phase.recurrenceMode,
                phaseType = phaseType,
                durationDays = calculateDurationDays(phase.billingPeriod, phase.billingCycleCount)
            )
        }

        // Extract different pricing phases
        val freeTrialPhase =
            pricingPhases.firstOrNull { it.phaseType == PricingPhaseType.FREE_TRIAL }
        val introductoryPhase =
            pricingPhases.firstOrNull { it.phaseType == PricingPhaseType.INTRODUCTORY }
        val regularPhase = pricingPhases.lastOrNull { it.phaseType == PricingPhaseType.REGULAR }
            ?: pricingPhases.last()

        return SubscriptionDetails(
            productId = productDetails.productId,
            productTitle = productDetails.title,
            productDescription = productDetails.description,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            pricingPhases = pricingPhases,
            freeTrialPhase = freeTrialPhase,
            introductoryPhase = introductoryPhase,
            regularPhase = regularPhase,
            formattedPrice = regularPhase.formattedPrice,
            priceAmountMicros = regularPhase.priceAmountMicros,
            priceCurrencyCode = regularPhase.priceCurrencyCode,
            hasFreeTrial = freeTrialPhase != null,
            freeTrialDays = freeTrialPhase?.durationDays,
            hasIntroductoryPrice = introductoryPhase != null,
            isActive = activePurchases.containsKey(productDetails.productId),
            offerTags = offer.offerTags
        )
    }

    private fun calculateDurationDays(billingPeriod: String, billingCycleCount: Int): Int? {
        // Parse ISO 8601 duration format (e.g., "P1M", "P1Y", "P7D")
        return try {
            val period = billingPeriod.removePrefix("P")
            val days = when {
                period.endsWith("D") -> period.removeSuffix("D").toInt()
                period.endsWith("W") -> period.removeSuffix("W").toInt() * 7
                period.endsWith("M") -> period.removeSuffix("M").toInt() * 30
                period.endsWith("Y") -> period.removeSuffix("Y").toInt() * 365
                else -> null
            }
            days?.let { it * billingCycleCount }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateActivePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            purchase.products.forEach { productId ->
                if (productId in subscriptionIds) {
                    activePurchases[productId] = purchase

                    // Auto-acknowledge purchases if not already acknowledged
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        BillingLogger.debug("Auto-acknowledging purchase for $productId")
                        billingClient.acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
        }

        // Notify purchase update with all active purchases
        notifyPurchaseUpdate(purchases)
    }

    private suspend fun notifySubscriptionUpdate(subscriptions: List<SubscriptionDetails>) {
        // Update StateFlows
        _subscriptionsFlow.value = subscriptions
        _activeSubscriptionsFlow.value = subscriptions.filter { it.isActive }

        // Update listener (for callback-based API)
        subscriptionUpdateListener?.let { listener ->
            withContext(Dispatchers.Main) {
                listener.onSubscriptionsUpdated(subscriptions)
            }
        }
    }

    private suspend fun notifyPurchaseUpdate(purchases: List<Purchase>) {
        purchaseUpdateListener?.let { listener ->
            withContext(Dispatchers.Main) {
                listener.onPurchasesUpdated(purchases)
            }
        }
    }

    private suspend fun handleFetchError(
        error: Exception,
        callback: ((Result<List<SubscriptionDetails>>) -> Unit)?
    ) {
        BillingLogger.error("Error fetching products: ${error.message}", error)

        // Notify with empty list on error
        subscriptionUpdateListener?.let { listener ->
            withContext(Dispatchers.Main) {
                listener.onSubscriptionsUpdated(emptyList())
            }
        }

        callback?.let {
            withContext(Dispatchers.Main) {
                it(Result.failure(error))
            }
        }
    }

    override fun getCachedProducts(): List<SubscriptionDetails> {
        return cachedSubscriptions.toList()
    }

    override fun subscribe(
        activity: Activity,
        productId: String,
        callback: (PurchaseResult) -> Unit
    ) {
        scope.launch {
            try {
                BillingLogger.debug("Starting subscription flow for $productId (auto-select plan)")

                // Find the subscription in cached products
                val subscription = cachedSubscriptions.firstOrNull { it.productId == productId }

                if (subscription != null) {
                    // Use the cached subscription details - call the detailed method
                    subscribeWithDetails(
                        activity,
                        productId,
                        subscription.basePlanId,
                        subscription.offerId,
                        callback
                    )
                } else {
                    // Query product details if not in cache
                    val productDetailsList =
                        billingClient.querySubscriptionDetailsById(listOf(productId))
                    val productDetails =
                        productDetailsList.firstOrNull { it.productId == productId }

                    if (productDetails == null) {
                        BillingLogger.error("Product not found: $productId")
                        withContext(Dispatchers.Main) {
                            callback(PurchaseResult.Error("Product not found", -1))
                        }
                        return@launch
                    }

                    // Auto-select first available offer
                    val firstOffer = productDetails.subscriptionOfferDetails?.firstOrNull()
                    if (firstOffer == null) {
                        BillingLogger.error("No offers available for product: $productId")
                        withContext(Dispatchers.Main) {
                            callback(PurchaseResult.Error("No offers available", -1))
                        }
                        return@launch
                    }

                    // Call the detailed method
                    subscribeWithDetails(
                        activity,
                        productId,
                        firstOffer.basePlanId,
                        firstOffer.offerId,
                        callback
                    )
                }
            } catch (e: Exception) {
                BillingLogger.error("Error during subscription: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(PurchaseResult.Error(e.message ?: "Unknown error", -1))
                }
            }
        }
    }

    private fun subscribeWithDetails(
        activity: Activity,
        productId: String,
        basePlanId: String,
        offerId: String?,
        callback: (PurchaseResult) -> Unit
    ) {
        scope.launch {
            try {
                BillingLogger.debug("Starting subscription flow for $productId with basePlan: $basePlanId, offer: $offerId")

                // Query product details
                val productDetailsList =
                    billingClient.querySubscriptionDetailsById(listOf(productId))
                val productDetails = productDetailsList.firstOrNull { it.productId == productId }

                if (productDetails == null) {
                    BillingLogger.error("Product not found: $productId")
                    withContext(Dispatchers.Main) {
                        callback(PurchaseResult.Error("Product not found", -1))
                    }
                    return@launch
                }

                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
                    ?.firstOrNull { offer ->
                        offer.basePlanId == basePlanId &&
                                (offerId == null || offer.offerId == offerId)
                    }

                if (subscriptionOfferDetails == null) {
                    BillingLogger.error("Offer not found for basePlan: $basePlanId, offer: $offerId")
                    withContext(Dispatchers.Main) {
                        callback(PurchaseResult.Error("Offer not found", -1))
                    }
                    return@launch
                }

                // Launch billing flow
                billingClient.launchBillingFlow(
                    activity,
                    productDetails,
                    subscriptionOfferDetails.offerToken
                ).collect { purchaseResult ->
                    // Update active purchases on success
                    if (purchaseResult is PurchaseResult.Success) {
                        // Refresh everything (purchases + products) in one efficient call
                        // fetchProducts already queries purchases, so no need to do it twice
                        fetchProducts(forceRefresh = true)
                    }

                    withContext(Dispatchers.Main) {
                        callback(purchaseResult)
                    }
                }
            } catch (e: Exception) {
                BillingLogger.error("Error during subscription: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(PurchaseResult.Error(e.message ?: "Unknown error", -1))
                }
            }
        }
    }

    override fun subscribe(
        activity: Activity,
        productId: String,
        basePlanId: String,
        offerId: String?,
        callback: (PurchaseResult) -> Unit
    ) {
        subscribeWithDetails(activity, productId, basePlanId, offerId, callback)
    }

    override fun hasActiveSubscription(
        productId: String,
        callback: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                val purchases = billingClient.queryPurchases()
                val hasActive = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            productId in purchase.products
                }

                withContext(Dispatchers.Main) {
                    callback(hasActive)
                }
            } catch (e: Exception) {
                BillingLogger.error("Error checking subscription status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    override fun hasAnyActiveSubscription(callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val purchases = billingClient.queryPurchases()
                val hasAny = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            purchase.products.any { it in subscriptionIds }
                }

                withContext(Dispatchers.Main) {
                    callback(hasAny)
                }
            } catch (e: Exception) {
                BillingLogger.error("Error checking subscription status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    override fun getActiveSubscription(): SubscriptionDetails? {
        return cachedSubscriptions.firstOrNull { it.isActive }
    }
}
