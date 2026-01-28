# BillingKit

Modern Android billing library for Google Play subscriptions with automatic connection, Flow support, and simplified API.

[![](https://jitpack.io/v/shibaprasadsahu/BillingKit.svg)](https://jitpack.io/#shibaprasadsahu/BillingKit)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

## ✨ Features

- ✅ **Two-Stream Flow** - Separate flows for available products and active subscriptions
- ✅ **Automatic Connection** - Billing connects and fetches products automatically
- ✅ **Simplified API** - Just pass product ID to subscribe
- ✅ **Lifecycle Aware** - Handles lifecycle events automatically
- ✅ **Thread Safe** - Built with coroutines and mutex protection

## 📦 Installation

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation("com.github.shibaprasadsahu:billingkit:0.1-alpha04")
}
```

## 🚀 Quick Start

### 1. Initialize in Application

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        BillingKit.initialize(
            context = this,
            subscriptionIds = listOf("premium_monthly", "premium_yearly"),
            logLevel = LogLevel.DEBUG,
            base64PublicKey = "YOUR_BASE64_PUBLIC_KEY" // Optional but recommended
        )
    }
}
```

**🔐 Security Setup (Recommended)**

To enable purchase signature verification:
1. Go to **Google Play Console** → **Your App** → **Monetization setup** → **Licensing**
2. Copy your **Base64-encoded RSA public key**
3. Pass it to `base64PublicKey` parameter

> If not provided, signature verification is **skipped** (less secure but optional)

### 2. Observe Products & Subscriptions

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val billingKit = BillingKit.getInstance()

        // Observe all available products (prices, details, etc.)
        lifecycleScope.launch {
            billingKit.productsFlow.collect { products ->
                updateProductUI(products)
            }
        }

        // Observe only active subscriptions
        lifecycleScope.launch {
            billingKit.activeSubscriptionsFlow.collect { activeSubs ->
                if (activeSubs.isNotEmpty()) {
                    enablePremiumFeatures()
                }
            }
        }
    }
}
```

### 3. Subscribe

```kotlin
billingKit.subscribe(this, "premium_monthly") { result ->
    when (result) {
        is PurchaseResult.Success -> showSuccess()
        is PurchaseResult.Error -> showError(result.message)
        is PurchaseResult.Cancelled -> {}
        is PurchaseResult.AlreadyOwned -> showAlreadyOwned()
    }
}
```

## 📱 Complete Compose Example

```kotlin
@Composable
fun SubscriptionScreen() {
    val billingKit = remember { BillingKit.getInstance() }
    val products by billingKit.productsFlow.collectAsState()
    val activeSubscriptions by billingKit.activeSubscriptionsFlow.collectAsState()

    LazyColumn {
        items(products) { product ->
            SubscriptionCard(
                title = product.productTitle,
                price = product.formattedPrice,
                isActive = product.isActive,
                onSubscribe = {
                    billingKit.subscribe(context as Activity, product.productId) { }
                }
            )
        }
    }
}
```

## 🎯 Alternative: Callback API

```kotlin
```kotlin
// Set listener to observe purchase updates (lifecycle-aware)
billingKit.setPurchaseUpdateListener(this) { owner, purchases ->
    handlePurchases(purchases)
}

// Clean up (though auto-cleanup is handled by lifecycle)
override fun onDestroy() {
    super.onDestroy()
    billingKit.removePurchaseUpdateListener()
}
```

## 🆓 Free Trial Support

BillingKit makes it easy to handle free trials and check user eligibility.

```kotlin
// Check if user is eligible for free trial
val isEligible = subscription.isUserEligibleForFreeTrial

// Subscribe with Free Trial control
// true  = Use free trial (if available)
// false = Skip free trial (charge immediately)
// null  = Auto-select best offer (default)
billingKit.subscribe(activity, "premium_monthly", useFreeTrial = true) { result ->
    // Handle result
}
```

## 🔄 Restore Purchases

Users can restore their existing purchases (e.g., after reinstalling the app or changing devices).

```kotlin
billingKit.restorePurchases { result ->
    result.onSuccess { activeSubscriptions ->
        if (activeSubscriptions.isNotEmpty()) {
            // Restore successful, user has active subscriptions
            showRestoreSuccess()
        } else {
            // No active subscriptions found
            showNoPurchasesFound()
        }
    }.onFailure { error ->
        showError(error.message)
    }
}
```

## 📚 Key APIs

### Check Subscription Status

```kotlin
billingKit.hasActiveSubscription("premium_monthly") { isActive ->
    if (isActive) enableFeatures()
}

billingKit.hasAnyActiveSubscription { hasAny ->
    if (hasAny) showPremiumUI()
}

val activeList = billingKit.getActiveSubscription() // Deprecated: Use activeSubscriptionsFlow
val products = billingKit.getCachedProducts()
```

### Manual Refresh (Optional)

```kotlin
// Lifecycle-aware (fetches on ON_START with debouncing)
billingKit.fetchProducts(this)

// Force refresh
billingKit.fetchProducts(forceRefresh = true)
```

## 🔥 Why BillingKit?

### Auto-Everything
- Products fetched automatically on initialize
- Purchases acknowledged automatically
- UI updates automatically via `productsFlow` and `activeSubscriptionsFlow`
- Multi-screen updates work automatically

### Simple & Powerful
```kotlin
// Before (complex)
billingClient.queryProductDetailsAsync(params) { result ->
    val details = result.productDetailsList
    val offer = details?.firstOrNull()?.subscriptionOfferDetails?.firstOrNull()
    val flowParams = BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(...)
        .build()
    billingClient.launchBillingFlow(activity, flowParams)
}

// After (simple)
billingKit.subscribe(activity, "premium_monthly") { }
```

### No Duplicate Fetches
- Smart debouncing (3-minute interval)
- Race condition protection
- Efficient caching
- Auto-retry with exponential backoff

## 🔐 Security

### Purchase Signature Verification

BillingKit includes **optional RSA signature verification** to prevent purchase tampering and fraud.

**How it works:**
- When a user completes a purchase, Google Play signs the purchase data with their private key
- BillingKit verifies this signature using your app's public key
- Invalid signatures are rejected automatically

**Setup:**

```kotlin
// Method 1: Simple initialization
BillingKit.initialize(
    context = this,
    subscriptionIds = listOf("premium_monthly"),
    base64PublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8..." // From Play Console
)

// Method 2: Builder pattern
BillingKit.initialize(this) {
    setSubscriptionIds(listOf("premium_monthly"))
    setBase64PublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8...")
    setLogLevel(LogLevel.DEBUG)
}
```

**Get Your Public Key:**
1. Open [Google Play Console](https://play.google.com/console)
2. Navigate to: **Your App** → **Monetization setup** → **Licensing**
3. Copy the **Base64-encoded RSA public key**
4. Paste it into your `base64PublicKey` parameter

**Important:**
- ⚠️ If `base64PublicKey` is **not provided**, signature verification is **skipped**
- ✅ **Recommended** for production apps to prevent fraud
- 🔒 Your public key is safe to include in your app (it's public by design)
- 📝 Logs warnings when verification is skipped (visible with `LogLevel.WARN` or higher)

## 🛡️ ProGuard

ProGuard rules are included automatically. No additional configuration needed!

## 📄 License

```
MIT License - Copyright (c) 2025 Shiba Prasad Sahu
```

## 👨‍💻 Author

**Shiba Prasad Sahu**
- GitHub: [@shibaprasadsahu](https://github.com/shibaprasadsahu)

---

⭐ Star this repo if it helped you!
