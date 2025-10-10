package com.shibaprasadsahu.billingkit

import com.shibaprasadsahu.billingkit.model.PurchaseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseResultTest {

    @Test
    fun `purchaseResult success contains correct data`() {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "token123"
        val orderId = "order456"

        // When
        val result = PurchaseResult.Success(
            productId = productId,
            purchaseToken = purchaseToken,
            orderId = orderId
        )

        // Then
        assertEquals(productId, result.productId)
        assertEquals(purchaseToken, result.purchaseToken)
        assertEquals(orderId, result.orderId)
        assertTrue(result is PurchaseResult.Success)
    }

    @Test
    fun `purchaseResult error contains message and code`() {
        // Given
        val errorMessage = "Billing unavailable"
        val errorCode = -1

        // When
        val result = PurchaseResult.Error(
            message = errorMessage,
            code = errorCode
        )

        // Then
        assertEquals(errorMessage, result.message)
        assertEquals(errorCode, result.code)
        assertTrue(result is PurchaseResult.Error)
    }

    @Test
    fun `purchaseResult cancelled is correct type`() {
        // When
        val result = PurchaseResult.Cancelled

        // Then
        assertTrue(result is PurchaseResult.Cancelled)
        assertEquals(PurchaseResult.Cancelled, result)
    }

    @Test
    fun `purchaseResult alreadyOwned is correct type`() {
        // When
        val result = PurchaseResult.AlreadyOwned

        // Then
        assertTrue(result is PurchaseResult.AlreadyOwned)
        assertEquals(PurchaseResult.AlreadyOwned, result)
    }

    @Test
    fun `purchaseResult sealed class hierarchy`() {
        // Given
        val results = listOf<PurchaseResult>(
            PurchaseResult.Success("id", "token", "order"),
            PurchaseResult.Error("error", -1),
            PurchaseResult.Cancelled,
            PurchaseResult.AlreadyOwned
        )

        // When
        results.forEach { result ->
            val typeCheck = when (result) {
                is PurchaseResult.Success -> "success"
                is PurchaseResult.Error -> "error"
                is PurchaseResult.Cancelled -> "cancelled"
                is PurchaseResult.AlreadyOwned -> "alreadyOwned"
            }

            // Then
            assertTrue(typeCheck in listOf("success", "error", "cancelled", "alreadyOwned"))
        }
    }
}
