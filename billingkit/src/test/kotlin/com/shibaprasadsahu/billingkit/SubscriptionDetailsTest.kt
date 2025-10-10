package com.shibaprasadsahu.billingkit

import com.shibaprasadsahu.billingkit.model.PricingPhase
import com.shibaprasadsahu.billingkit.model.PricingPhaseType
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDetailsTest {

    @Test
    fun `subscriptionDetails with free trial`() {
        // Given
        val freeTrialPhase = PricingPhase(
            formattedPrice = "Free",
            priceAmountMicros = 0,
            priceCurrencyCode = "USD",
            billingPeriod = "P7D",
            billingCycleCount = 1,
            recurrenceMode = 2,
            phaseType = PricingPhaseType.FREE_TRIAL,
            durationDays = 7
        )

        val regularPhase = PricingPhase(
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 1,
            recurrenceMode = 1,
            phaseType = PricingPhaseType.REGULAR,
            durationDays = 30
        )

        // When
        val subscription = SubscriptionDetails(
            productId = "premium_monthly",
            productTitle = "Premium Monthly",
            productDescription = "Premium subscription",
            basePlanId = "base-plan",
            offerId = "free-trial-offer",
            offerToken = "token",
            pricingPhases = listOf(freeTrialPhase, regularPhase),
            freeTrialPhase = freeTrialPhase,
            introductoryPhase = null,
            regularPhase = regularPhase,
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            hasFreeTrial = true,
            freeTrialDays = 7,
            hasIntroductoryPrice = false,
            isActive = false,
            offerTags = listOf("trial")
        )

        // Then
        assertEquals("premium_monthly", subscription.productId)
        assertTrue(subscription.hasFreeTrial)
        assertEquals(7, subscription.freeTrialDays)
        assertFalse(subscription.hasIntroductoryPrice)
        assertFalse(subscription.isActive)
        assertNotNull(subscription.freeTrialPhase)
        assertNull(subscription.introductoryPhase)
    }

    @Test
    fun `subscriptionDetails with introductory price`() {
        // Given
        val introPhase = PricingPhase(
            formattedPrice = "$4.99",
            priceAmountMicros = 4990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 3,
            recurrenceMode = 2,
            phaseType = PricingPhaseType.INTRODUCTORY,
            durationDays = 90
        )

        val regularPhase = PricingPhase(
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 1,
            recurrenceMode = 1,
            phaseType = PricingPhaseType.REGULAR,
            durationDays = 30
        )

        // When
        val subscription = SubscriptionDetails(
            productId = "premium_monthly",
            productTitle = "Premium Monthly",
            productDescription = "Premium subscription",
            basePlanId = "base-plan",
            offerId = "intro-offer",
            offerToken = "token",
            pricingPhases = listOf(introPhase, regularPhase),
            freeTrialPhase = null,
            introductoryPhase = introPhase,
            regularPhase = regularPhase,
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            hasFreeTrial = false,
            freeTrialDays = null,
            hasIntroductoryPrice = true,
            isActive = false,
            offerTags = listOf("intro")
        )

        // Then
        assertFalse(subscription.hasFreeTrial)
        assertNull(subscription.freeTrialDays)
        assertTrue(subscription.hasIntroductoryPrice)
        assertNotNull(subscription.introductoryPhase)
        assertNull(subscription.freeTrialPhase)
        assertEquals("$4.99", subscription.introductoryPhase?.formattedPrice)
    }

    @Test
    fun `subscriptionDetails active status`() {
        // Given
        val regularPhase = PricingPhase(
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 1,
            recurrenceMode = 1,
            phaseType = PricingPhaseType.REGULAR,
            durationDays = 30
        )

        // When
        val subscription = SubscriptionDetails(
            productId = "premium_monthly",
            productTitle = "Premium Monthly",
            productDescription = "Premium subscription",
            basePlanId = "base-plan",
            offerId = null,
            offerToken = "token",
            pricingPhases = listOf(regularPhase),
            freeTrialPhase = null,
            introductoryPhase = null,
            regularPhase = regularPhase,
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            hasFreeTrial = false,
            freeTrialDays = null,
            hasIntroductoryPrice = false,
            isActive = true,
            offerTags = emptyList()
        )

        // Then
        assertTrue(subscription.isActive)
        assertEquals("premium_monthly", subscription.productId)
        assertEquals("$9.99", subscription.formattedPrice)
    }

    @Test
    fun `pricingPhase types are correct`() {
        // Given
        val freeTrialPhase = PricingPhase(
            formattedPrice = "Free",
            priceAmountMicros = 0,
            priceCurrencyCode = "USD",
            billingPeriod = "P7D",
            billingCycleCount = 1,
            recurrenceMode = 2,
            phaseType = PricingPhaseType.FREE_TRIAL,
            durationDays = 7
        )

        val introPhase = PricingPhase(
            formattedPrice = "$4.99",
            priceAmountMicros = 4990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 1,
            recurrenceMode = 2,
            phaseType = PricingPhaseType.INTRODUCTORY,
            durationDays = 30
        )

        val regularPhase = PricingPhase(
            formattedPrice = "$9.99",
            priceAmountMicros = 9990000,
            priceCurrencyCode = "USD",
            billingPeriod = "P1M",
            billingCycleCount = 1,
            recurrenceMode = 1,
            phaseType = PricingPhaseType.REGULAR,
            durationDays = 30
        )

        // Then
        assertEquals(PricingPhaseType.FREE_TRIAL, freeTrialPhase.phaseType)
        assertEquals(PricingPhaseType.INTRODUCTORY, introPhase.phaseType)
        assertEquals(PricingPhaseType.REGULAR, regularPhase.phaseType)
        assertEquals(0, freeTrialPhase.priceAmountMicros)
        assertTrue(introPhase.priceAmountMicros > 0)
        assertTrue(regularPhase.priceAmountMicros > 0)
    }
}
