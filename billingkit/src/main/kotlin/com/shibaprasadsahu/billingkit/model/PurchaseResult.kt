package com.shibaprasadsahu.billingkit.model

/**
 * Result of a subscription purchase operation
 */
sealed class PurchaseResult {
    data class Success(
        val productId: String,
        val purchaseToken: String,
        val orderId: String
    ) : PurchaseResult()

    data class Error(
        val message: String,
        val code: Int
    ) : PurchaseResult()

    data object Cancelled : PurchaseResult()
    data object AlreadyOwned : PurchaseResult()
}
