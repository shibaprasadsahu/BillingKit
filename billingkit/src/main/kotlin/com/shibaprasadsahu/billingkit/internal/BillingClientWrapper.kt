package com.shibaprasadsahu.billingkit.internal

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.shibaprasadsahu.billingkit.model.PurchaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Internal wrapper around Google Play BillingClient
 */
internal class BillingClientWrapper(
    context: Context,
    private val base64PublicKey: String? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Initialize billing client immediately (not lazy) to start connection automatically
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            handlePurchaseUpdate(billingResult, purchases)
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()
        .also { client -> startConnection(client) }

    private var purchaseUpdateCallback: ((BillingResult, List<Purchase>?) -> Unit)? = null
    private var onConnectionEstablishedCallback: (() -> Unit)? = null

    fun setOnConnectionEstablishedCallback(callback: () -> Unit) {
        onConnectionEstablishedCallback = callback
    }

    private fun startConnection(client: BillingClient) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    BillingLogger.info("Billing client connected successfully")
                    // Notify that connection is established
                    onConnectionEstablishedCallback?.invoke()
                } else {
                    BillingLogger.error("Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                BillingLogger.warn("Billing service disconnected, will retry connection")
                // Retry connection - use the client parameter to avoid accessing billingClient property
                startConnection(client)
            }
        })
    }

    private fun handlePurchaseUpdate(billingResult: BillingResult, purchases: List<Purchase>?) {
        BillingLogger.debug("Purchase update: ${billingResult.responseCode}, purchases: ${purchases?.size ?: 0}")
        purchaseUpdateCallback?.invoke(billingResult, purchases)
    }

    /**
     * Ensure billing client is ready
     */
    private suspend fun ensureReady(): Boolean = suspendCancellableCoroutine { continuation ->
        if (billingClient.isReady) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(true)
                } else {
                    BillingLogger.error("Failed to connect: ${billingResult.debugMessage}")
                    continuation.resume(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                continuation.resume(false)
            }
        })
    }

    /**
     * Query subscription product details by IDs
     */
    suspend fun querySubscriptionDetailsById(
        productIds: List<String>
    ): List<ProductDetails> {
        if (!ensureReady()) {
            BillingLogger.error("Billing client not ready")
            return emptyList()
        }

        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = productDetailsResult.productDetailsList
                    val unfetchedProducts = productDetailsResult.unfetchedProductList

                    if (unfetchedProducts.isNotEmpty()) {
                        BillingLogger.warn("Failed to fetch ${unfetchedProducts.size} products: $unfetchedProducts")
                    }

                    BillingLogger.debug("Queried ${productDetailsList.size} product details")
                    continuation.resume(productDetailsList)
                } else {
                    BillingLogger.error("Failed to query products: ${billingResult.debugMessage}")
                    continuation.resume(emptyList())
                }
            }
        }
    }

    /**
     * Query current purchases
     */
    suspend fun queryPurchases(): List<Purchase> {
        if (!ensureReady()) {
            BillingLogger.error("Billing client not ready")
            return emptyList()
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = billingClient.queryPurchasesAsync(params)

        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLogger.debug("Queried ${result.purchasesList.size} purchases")
            return result.purchasesList
        } else {
            BillingLogger.error("Failed to query purchases: ${result.billingResult.debugMessage}")
            return emptyList()
        }
    }

    /**
     * Launch billing flow for subscription
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String
    ): Flow<PurchaseResult> = callbackFlow {
        if (!ensureReady()) {
            trySend(PurchaseResult.Error("Billing client not ready", -1))
            close()
            return@callbackFlow
        }

        purchaseUpdateCallback = { billingResult, purchases ->
            scope.launch {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        val purchase = purchases?.firstOrNull()
                        if (purchase != null) {
                            // Verify purchase signature before processing
                            val originalJson = purchase.originalJson
                            val signature = purchase.signature

                            if (originalJson.isEmpty() || signature.isEmpty()) {
                                BillingLogger.error("Purchase data missing originalJson or signature")
                                trySend(PurchaseResult.Error("Purchase data incomplete", billingResult.responseCode))
                                close()
                                return@launch
                            }

                            val isValid = Security.verifyPurchase(
                                base64PublicKey = base64PublicKey,
                                signedData = originalJson,
                                signature = signature
                            )

                            if (!isValid) {
                                BillingLogger.error("Purchase signature verification failed! Purchase rejected.")
                                trySend(PurchaseResult.Error("Purchase verification failed - signature invalid", billingResult.responseCode))
                                close()
                                return@launch
                            }

                            // Acknowledge the purchase after verification
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                if (!purchase.isAcknowledged) {
                                    acknowledgePurchase(purchase.purchaseToken)
                                }
                                trySend(
                                    PurchaseResult.Success(
                                        productId = purchase.products.firstOrNull() ?: "",
                                        purchaseToken = purchase.purchaseToken,
                                        orderId = purchase.orderId ?: ""
                                    )
                                )
                            }
                        } else {
                            trySend(PurchaseResult.Error("Purchase completed but no data received", billingResult.responseCode))
                        }
                        close()
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        trySend(PurchaseResult.Cancelled)
                        close()
                    }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        trySend(PurchaseResult.AlreadyOwned)
                        close()
                    }
                    else -> {
                        trySend(
                            PurchaseResult.Error(
                                billingResult.debugMessage,
                                billingResult.responseCode
                            )
                        )
                        close()
                    }
                }
            }
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            trySend(PurchaseResult.Error(result.debugMessage, result.responseCode))
            close()
        }

        awaitClose {
            purchaseUpdateCallback = null
        }
    }

    /**
     * Acknowledge a purchase
     */
    suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        if (!ensureReady()) return false

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    BillingLogger.info("Purchase acknowledged successfully")
                    continuation.resume(true)
                } else {
                    BillingLogger.error("Failed to acknowledge purchase: ${billingResult.debugMessage}")
                    continuation.resume(false)
                }
            }
        }
    }

    /**
     * End connection and cleanup
     */
    fun endConnection() {
        billingClient.endConnection()
        BillingLogger.info("Billing client connection ended")
    }
}
