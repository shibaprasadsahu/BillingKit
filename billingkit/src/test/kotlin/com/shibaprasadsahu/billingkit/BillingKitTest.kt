package com.shibaprasadsahu.billingkit

import com.shibaprasadsahu.billingkit.api.BillingKit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BillingKit that don't require BillingClient initialization.
 * 
 * Note: Tests involving BillingClient.initialize() require real Android context
 * and are tested in BillingKitInstrumentationTest.
 */
class BillingKitTest {

    @After
    fun tearDown() {
        BillingKit.destroy()
    }

    @Test
    fun `getInstance throws exception when not initialized`() {
        // Given - not initialized
        var thrownException: IllegalStateException? = null

        // When
        try {
            BillingKit.getInstance()
        } catch (e: IllegalStateException) {
            thrownException = e
        }

        // Then
        assertNotNull(thrownException)
        assertTrue(thrownException!!.message!!.contains("not initialized"))
    }

    @Test
    fun `isInitialized returns false before initialization`() {
        // When
        val result = BillingKit.isInitialized()

        // Then
        assertFalse(result)
    }
}
