package com.shibaprasadsahu.billingkit.model

/**
 * Detailed information about a subscription product
 */
data class SubscriptionDetails(
    // Product identification
    val productId: String,              // The subscription product ID from Google Play Console
    val productTitle: String,           // Localized title (e.g., "Premium Monthly")
    val productDescription: String,     // Localized description

    // Plan and offer details
    val basePlanId: String,            // The base plan ID (e.g., "monthly-plan")
    val offerId: String?,              // Optional offer ID for promotional offers
    val offerToken: String,            // Token needed to make the purchase

    // Pricing information
    val pricingPhases: List<PricingPhase>, // All pricing phases (trial, intro, regular)

    // Offer details
    val freeTrialPhase: PricingPhase?,  // Free trial phase if available
    val introductoryPhase: PricingPhase?, // Introductory pricing if available
    val regularPhase: PricingPhase,     // Regular pricing phase

    // Formatted pricing for UI display
    val formattedPrice: String,         // Formatted regular price (e.g., "$9.99/month")
    val priceAmountMicros: Long,        // Price in micros (e.g., 9990000 for $9.99)
    val priceCurrencyCode: String,      // ISO 4217 currency code (e.g., "USD")

    // Offer information
    val hasFreeTrial: Boolean,          // Whether this offer includes a free trial
    val freeTrialDays: Int?,            // Number of free trial days if available
    val hasIntroductoryPrice: Boolean,  // Whether this offer has introductory pricing

    // Subscription status
    val isActive: Boolean = false,      // Whether user currently has an active subscription

    // Additional metadata
    val offerTags: List<String> = emptyList() // Tags for categorizing offers (e.g., "best-value", "popular")
)

/**
 * Represents a pricing phase in a subscription
 * (e.g., free trial, introductory price, regular price)
 */
data class PricingPhase(
    val formattedPrice: String,        // Formatted price with currency symbol (e.g., "$9.99")
    val priceAmountMicros: Long,       // Price in micro-units (1,000,000 micros = 1 currency unit)
    val priceCurrencyCode: String,     // ISO 4217 currency code (e.g., "USD", "EUR", "INR")
    val billingPeriod: String,         // ISO 8601 format (e.g., "P1M" = 1 month, "P1Y" = 1 year)
    val billingCycleCount: Int,        // Number of billing cycles (0 = infinite)
    val recurrenceMode: Int,           // 1 = NON_RECURRING, 2 = FINITE_RECURRING, 3 = INFINITE_RECURRING

    // Convenience fields
    val phaseType: PricingPhaseType,   // Type of pricing phase
    val durationDays: Int?             // Duration in days (calculated from billingPeriod)
)

/**
 * Type of pricing phase
 */
enum class PricingPhaseType {
    FREE_TRIAL,      // Free trial period
    INTRODUCTORY,    // Discounted introductory pricing
    REGULAR          // Regular pricing
}
