# BillingKit - Development Guide

## Architecture Overview

BillingKit is an Android billing library built on Google Play Billing Library 8.3.0, simplifying
subscription management with Kotlin Coroutines and StateFlow.

### Core Components

```
┌──────────────────────────────────────┐
│            Public API                │
│  BillingKit (singleton)              │
│  SubscriptionDetails (model)         │
│  PurchaseResult (sealed class)       │
│  LogLevel (enum)                     │
│  PurchaseUpdateListener (callback)   │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│       Internal Implementation        │
│  SubscriptionManagerImpl             │
│  - Auto-fetch on connection          │
│  - productsFlow & activeSubscriptions│
│  - Auto-acknowledge purchases        │
│  - Lifecycle-aware fetching          │
│  - RSA signature verification        │
│  - Persistent device ID              │
└───────┬──────────────────────────────┘
        │
┌───────▼─────────────────────────────┐
│  BillingClientWrapper               │
│  Security (RSA verification)        │
│  BillingDeviceIdManager (PersistID) │
│  BillingLogger                      │
└─────────────────────────────────────┘
```

## File Structure

```
billingkit/src/main/kotlin/com/shibaprasadsahu/billingkit/
├── api/
│   ├── BillingKit.kt              # Public singleton API
│   ├── BillingListener.kt         # PurchaseUpdateListener fun interface
│   └── SubscriptionManager.kt     # Manager interface
├── model/
│   ├── SubscriptionDetails.kt     # Product details + pricing phases
│   ├── PricingPhase.kt            # Pricing phase model
│   ├── PricingPhaseType.kt        # FREE_TRIAL / INTRODUCTORY / REGULAR
│   └── PurchaseResult.kt          # Sealed class: Success/Error/Cancelled/AlreadyOwned
├── internal/
│   ├── SubscriptionManagerImpl.kt # Core implementation
│   ├── BillingClientWrapper.kt    # Play Billing abstraction
│   ├── BillingDeviceIdManager.kt  # Persistent device ID via PersistID
│   ├── Security.kt                # RSA purchase signature verification
│   └── BillingLogger.kt           # Structured logging
└── LogLevel.kt                    # VERBOSE/DEBUG/INFO/WARN/ERROR/NONE

billingkit/src/test/kotlin/
├── BillingKitTest.kt              # Singleton & initialization tests
├── SubscriptionDetailsTest.kt     # Model tests
└── PurchaseResultTest.kt          # Sealed class tests
```

## Public API

### Initialization

```kotlin
// Simple
BillingKit.initialize(
    context = this,
    subscriptionIds = listOf("premium_monthly", "premium_yearly"),
    logLevel = LogLevel.DEBUG,
    base64PublicKey = "YOUR_KEY" // optional — enables RSA signature verification
)

// Builder pattern
BillingKit.initialize(this) {
    setSubscriptionIds(listOf("premium_monthly"))
    setBase64PublicKey("YOUR_KEY")
    setLogLevel(LogLevel.DEBUG)
}
```

### Reactive Flows

```kotlin
billingKit.productsFlow.collect { products -> }           // All products
billingKit.activeSubscriptionsFlow.collect { subs -> }    // Active only
```

### Subscribe

```kotlin
billingKit.subscribe(activity, "premium_monthly") { result -> }
billingKit.subscribe(activity, "premium_monthly", useFreeTrial = true) { result -> }
billingKit.subscribe(activity, "premium_monthly", basePlanId, offerId) { result -> }
```

### Other APIs

```kotlin
billingKit.restorePurchases { result -> }
billingKit.hasActiveSubscription("premium_monthly") { isActive -> }
billingKit.hasAnyActiveSubscription { hasAny -> }
billingKit.getFreeTrialOffer("premium_monthly")
billingKit.getRegularOffer("premium_monthly")
billingKit.fetchProducts(lifecycleOwner)
billingKit.fetchProducts(forceRefresh = true)
billingKit.getCachedProducts()
suspend billingKit.getDeviceId(): String  // Survives reinstalls via PersistID
billingKit.setPurchaseUpdateListener(lifecycleOwner) { owner, purchases -> }
billingKit.removePurchaseUpdateListener()
```

### PurchaseResult

```kotlin
sealed class PurchaseResult {
    data class Success(val productId: String, val purchaseToken: String, val orderId: String)
    data class Error(val message: String, val code: Int)
    data object Cancelled
    data object AlreadyOwned
}
```

## Key Design Decisions

- **Eager initialization**: Starts billing connection immediately on `initialize()`
- **3-minute debouncing**: Prevents excessive fetches; override with `forceRefresh = true`
- **Auto-retry**: 3 attempts with exponential backoff (3s/6s/9s)
- **Auto-acknowledge**: Purchases acknowledged automatically in `updateActivePurchases()`
- **Lifecycle-aware**: Fetches trigger on `ON_START`; listeners auto-clean on `ON_DESTROY`
- **Mutex + AtomicBoolean**: Thread-safe concurrency control
- **SupervisorJob**: Failure isolation for coroutines
- **`api` dependency**: `billing-ktx` exposed transitively so consumers don't need to add it

## Dependencies

Managed via `gradle/libs.versions.toml`:

| Dependency                          | Version        |
|-------------------------------------|----------------|
| Google Play Billing (`billing-ktx`) | 8.3.0          |
| Kotlin                              | 2.3.20         |
| Coroutines                          | 1.10.2         |
| Lifecycle Runtime KTX               | 2.10.0         |
| Android Gradle Plugin               | 9.1.1          |
| Gradle                              | 9.4.1          |
| Java / JVM Target                   | 21             |
| Compile SDK                         | 37, Min SDK 21 |

## Testing

Run: `./gradlew test`

Stack: JUnit 4, Mockito, Mockito-Kotlin, Coroutines Test, Turbine, Robolectric 4.16.1

## CI/CD

**CI** (`.github/workflows/ci.yml`): Runs on push to `main`/`dev` and PRs to `main`

- Jobs: Lint → Unit Tests → Build AAR (sequential, cancel-on-concurrent)

**Release** (`.github/workflows/release.yml`): Triggered by numeric tags (e.g., `0.1-alpha08`)

- Runs tests → builds AAR → creates GitHub Release → notifies JitPack

**JitPack** (`jitpack.yml`):

```yaml
jdk:
  - openjdk17
install:
  - ./gradlew clean build publishToMavenLocal -x test
```

## Publishing

```bash
# 1. Update version in billingkit/build.gradle.kts
# 2. Tag and push
git tag 0.1-alpha08
git push origin 0.1-alpha08
```

## ProGuard

Consumer rules in `billingkit/consumer-rules.pro` — included automatically. No action needed by
library consumers.

## Known Limitations

1. **Subscriptions only** — In-app products not supported
2. **Google Play required** — No alternative billing
3. **Min SDK 21** — Android 5.0+

## Contributing

Prerequisites: Android Studio Hedgehog+, JDK 21, Kotlin 2.3.20+, Gradle 9.4.1

```bash
git clone https://github.com/shibaprasadsahu/billingkit.git
./gradlew test lint
```

PR process: branch from `main` → write tests → run `./gradlew test lint` → submit PR
