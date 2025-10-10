# BillingKit - Development Guide

## Architecture Overview

BillingKit is a modern Android billing library built on top of Google Play Billing Library 8.0.0, designed to simplify subscription management with Kotlin Coroutines and StateFlow.

### Core Components

```
┌──────────────────────────────────┐
│         Public API               │
│  BillingKit (singleton)          │
│  SubscriptionDetails (model)     │
│  PurchaseResult (sealed class)   │
│  LogLevel (enum)                 │
│  Listeners (fun interfaces)      │
└────────────┬─────────────────────┘
             │
┌────────────▼─────────────────────┐
│  Internal Implementation         │
│  SubscriptionManagerImpl         │
│  - Auto-fetch on connection      │
│  - StateFlow subscriptions       │
│  - Auto-acknowledge purchases    │
│  - Lifecycle-aware fetching      │
└────────────┬─────────────────────┘
             │
     ┌───────┴──────────┐
     │                  │
┌────▼──────────┐  ┌────▼─────────┐
│ BillingClient │  │  Subscription│
│ Wrapper       │  │  Manager     │
└───────┬───────┘  └──────────────┘
        │
    ┌───┴────┐
    │        │
┌───▼──┐ ┌──▼────┐
│ Play │ │ State │
│ Store│ │ Flow  │
└──────┘ └───────┘
```

## Key Design Patterns

### 1. Singleton Pattern
`BillingKit` - Thread-safe initialization with double-checked locking

### 2. Observer Pattern
- StateFlow for reactive updates
- Callback listeners for event handling

### 3. Strategy Pattern
`PurchaseResult` sealed class - Type-safe result handling

### 4. Builder Pattern
`BillingKit.Builder()` - Fluent configuration

## Performance Optimizations

### Asynchronous Operations
- All billing operations use `Dispatchers.IO`
- Main thread operations use `Dispatchers.Main`
- Parallel execution with `async/await`

### Caching Strategy
- `StateFlow` maintains in-memory subscription cache
- Background auto-fetch on billing connection
- Near-zero latency for cached reads

### Smart Fetching
- **Debouncing**: 3-minute minimum interval between fetches
- **Auto-retry**: 3 attempts with exponential backoff (3s/6s/9s)
- **Lifecycle-aware**: Fetches only when Activity is STARTED
- **Duplicate prevention**: Eliminates redundant queries

### Concurrency Control
- `Mutex` ensures thread-safe operations
- `AtomicBoolean` prevents concurrent fetches
- `SupervisorJob` provides failure isolation
- Eager initialization for auto-connect

## File Structure

```
billingkit/
├── src/main/kotlin/com/shibaprasadsahu/billingkit/
│   ├── api/
│   │   ├── BillingKit.kt                  # Public singleton API
│   │   ├── SubscriptionManager.kt         # Manager interface
│   │   └── SubscriptionManagerImpl.kt     # Core implementation
│   ├── model/
│   │   ├── SubscriptionDetails.kt         # Product details model
│   │   ├── PricingPhase.kt                # Pricing phase model
│   │   ├── PricingPhaseType.kt            # Phase type enum
│   │   └── PurchaseResult.kt              # Result sealed class
│   ├── listener/
│   │   └── BillingListener.kt             # Fun interfaces for callbacks
│   ├── internal/
│   │   ├── BillingClientWrapper.kt        # Billing client abstraction
│   │   └── BillingLogger.kt               # Structured logging
│   └── LogLevel.kt                        # Log level enum
│
├── src/test/kotlin/
│   ├── BillingKitTest.kt                  # Singleton & initialization tests
│   ├── SubscriptionDetailsTest.kt         # Model tests
│   └── PurchaseResultTest.kt              # Result type tests
│
└── src/androidTest/kotlin/
    └── BillingKitInstrumentationTest.kt   # Integration tests
```

## API Design Principles

### 1. Minimal Public Surface
Public API consists of:
- `BillingKit` class with companion object
- `SubscriptionDetails` data model
- `PurchaseResult` sealed class
- `SubscriptionUpdateListener` fun interface
- `PurchaseUpdateListener` fun interface
- `LogLevel` enum

All implementation details are marked `internal`.

### 2. Dual API Approach
Provides both reactive and callback-based APIs:

**Reactive (StateFlow)**:
```kotlin
billingKit.subscriptionsFlow.collect { subscriptions ->
    updateUI(subscriptions)
}
```

**Callback**:
```kotlin
billingKit.setSubscriptionUpdateListener { subscriptions ->
    updateUI(subscriptions)
}
```

### 3. Simplified Subscribe
Only requires product ID - auto-selects base plan and offer:
```kotlin
billingKit.subscribe(activity, "premium_monthly") { result ->
    // Handle result
}
```

### 4. Automatic Operations
- **Auto-connect**: Billing connects on `initialize()`
- **Auto-fetch**: Products/purchases load automatically
- **Auto-acknowledge**: Purchases acknowledged automatically
- **Auto-retry**: Failed operations retry with backoff

## Testing Strategy

### Modern Test Stack
- **JUnit 4** - Testing framework
- **Mockito** - Mocking framework
- **Mockito-Kotlin** - Kotlin-friendly mocking DSL
- **Coroutines Test** - Suspending function testing
- **Turbine** - Flow testing utilities
- **AndroidX Test** - Android instrumentation testing

### Test Coverage
1. **BillingKitTest** - Initialization, singleton, builder pattern, validation
2. **SubscriptionDetailsTest** - Model validation, free trial, intro pricing
3. **PurchaseResultTest** - Sealed class hierarchy, type checks
4. **BillingKitInstrumentationTest** - Real Android context, flows, listeners

### Testing Practices
- Given-When-Then structure
- Proper cleanup with `@After` hooks
- Thread-safe test isolation
- Mock Android components (Context)
- No blocking code in tests

## Dependencies

### Core (Required)
- `billing-ktx:8.0.0` - Google Play Billing Library (exposed as `api`)
- `kotlinx-coroutines-android` - Async operations
- `lifecycle-runtime-ktx` - Lifecycle awareness

### Testing
- `junit:4.13.2` - Test framework only

All dependencies managed via `libs.versions.toml`.

## ProGuard Configuration

### Keep Rules (consumer-rules.pro)
**Optimized for minimal size** - Only essential rules included:

```proguard
# Public API
-keep public class com.shibaprasadsahu.billingkit.api.BillingKit {
    public *;
}

# Models - Data classes
-keep class com.shibaprasadsahu.billingkit.model.** { *; }

# Enums
-keep enum com.shibaprasadsahu.billingkit.LogLevel { *; }

# Listeners
-keep interface com.shibaprasadsahu.billingkit.listener.** { *; }

# Logger - Critical for release builds
-keep class com.shibaprasadsahu.billingkit.internal.BillingLogger {
    public static *** configure(...);
    public static *** verbose(...);
    public static *** debug(...);
    public static *** info(...);
    public static *** warn(...);
    public static *** error(...);
}

# Kotlin
-keepattributes Signature
-keepattributes *Annotation*
```

### Optimization Strategy
- **No unnecessary keeps**: Internal classes are obfuscated
- **No Billing library rules**: Exposed via `api` dependency
- **No coroutines rules**: Standard library handles it
- **Minimal attribute preservation**: Only Signature and Annotations
- **Result**: ~115KB AAR size

## Technical Decisions

### Why Eager Initialization?
**Problem**: Lazy initialization caused StackOverflowError and delayed connection.
**Solution**: Eager initialization in constructor starts connection immediately.

### Why Single Method Listeners?
**Problem**: Multiple callback methods (onSuccess/onError) complicated error handling.
**Solution**: `fun interface` with single method returns empty list on error.

### Why Auto-Acknowledge?
**Problem**: Developers often forget to acknowledge purchases.
**Solution**: Auto-acknowledge in `updateActivePurchases()` ensures proper flow.

### Why Expose Billing Dependency?
**Problem**: Users had to declare billing library separately.
**Solution**: Changed from `implementation` to `api` for transitive exposure.

### Why Private Helper Methods?
**Problem**: Public interface methods calling each other caused recursion.
**Solution**: Private `subscribeWithDetails()` helper breaks recursion cycle.

### Why 3-Minute Debouncing?
**Problem**: Frequent fetches waste resources and trigger rate limits.
**Solution**: Smart debouncing with force-refresh override for critical operations.

### Why No Deprecation?
**Problem**: Library is pre-alpha, breaking changes expected.
**Solution**: Complete removal instead of deprecation cycle.

## Performance Benchmarks

### Cold Start (First Access)
- Billing connection: ~500-1000ms
- Product fetch: ~200-500ms (network dependent)
- Purchase query: ~100-300ms (network dependent)

### Warm Access (Cached)
- Flow emission: <1ms (hot stream)
- Cached product access: <1ms

### Concurrency
- Thread-safe operations using Mutex
- Non-blocking main thread execution
- Parallel fetch operations (products + purchases)

## Security Considerations

### Data Protection
- No sensitive data stored locally
- Purchase tokens handled securely by Play Billing
- ProGuard rules protect internal implementation

### Purchase Verification
- Server-side verification recommended (not handled by library)
- Auto-acknowledgement only for verified purchases

## Known Limitations

1. **Subscription only** - In-app products not yet supported
2. **Google Play required** - No alternative billing support
3. **Android 5.0+** - Minimum SDK 21
4. **Single base plan** - Auto-selects first available plan

## CI/CD Pipeline

### Continuous Integration (.github/workflows/ci.yml)
Three parallel jobs with proper dependency management:

1. **Lint Check** - Code quality validation (runs independently)
2. **Unit Tests** - 11 tests covering models and API contracts (runs independently)  
3. **Build** - Release AAR compilation (runs after lint and test pass)

**Features**:
- Concurrency control (cancels in-progress runs)
- Test result publishing with EnricoMi/publish-unit-test-result-action
- Artifact uploads for reports
- Runs on `main` and `dev` branches plus all PRs

### Release Workflow (.github/workflows/release.yml)
- Triggered by tags matching `[0-9]*` (e.g., `0.1-alpha01`)
- Runs tests before release
- Creates GitHub Release with AAR
- Triggers JitPack build automatically
- Generates release notes with installation instructions

### JitPack Configuration (jitpack.yml)
```yaml
jdk:
  - openjdk17
install:
  - ./gradlew clean build publishToMavenLocal -x test
```

## Publishing

### Version Format
- **Alpha**: `0.x-alphaXX` (e.g., `0.1-alpha01`)
- **Beta**: `0.x-betaXX` (future)
- **Stable**: `1.x.x` (future)

### Publishing Steps
1. Update version in `build.gradle.kts`
2. Create git tag: `git tag 0.1-alpha01`
3. Push tag: `git push origin 0.1-alpha01`
4. GitHub Actions automatically:
   - Runs tests
   - Builds AAR
   - Creates release
   - Triggers JitPack

### Installation
```kotlin
// settings.gradle.kts
maven { url = uri("https://jitpack.io") }

// build.gradle.kts
dependencies {
    implementation("com.github.shibaprasadsahu:billingkit:0.1-alpha01")
}
```

## Usage Examples

### Basic Setup
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var billingKit: BillingKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize
        BillingKit.initialize(
            context = this,
            subscriptionIds = listOf("premium_monthly", "premium_yearly"),
            logLevel = LogLevel.DEBUG
        )

        billingKit = BillingKit.getInstance()
    }
}
```

### Observe Subscriptions (Flow)
```kotlin
lifecycleScope.launch {
    billingKit.subscriptionsFlow.collect { subscriptions ->
        subscriptions.forEach { subscription ->
            Log.d("Billing", "Product: ${subscription.productId}")
            Log.d("Billing", "Price: ${subscription.formattedPrice}")
            Log.d("Billing", "Active: ${subscription.isActive}")
        }
    }
}
```

### Observe Subscriptions (Callback)
```kotlin
billingKit.setSubscriptionUpdateListener { subscriptions ->
    updateUI(subscriptions)
}
```

### Subscribe
```kotlin
billingKit.subscribe(this, "premium_monthly") { result ->
    when (result) {
        is PurchaseResult.Success -> {
            showSuccess("Subscription activated!")
        }
        is PurchaseResult.Error -> {
            showError(result.message)
        }
        is PurchaseResult.Cancelled -> {
            showMessage("Purchase cancelled")
        }
        is PurchaseResult.AlreadyOwned -> {
            showMessage("Already subscribed")
        }
    }
}
```

### Check Active Subscription
```kotlin
val activeSubscription = billingKit.getActiveSubscription()
if (activeSubscription != null) {
    Log.d("Billing", "Active: ${activeSubscription.productId}")
}
```

### Check Specific Subscription
```kotlin
billingKit.hasActiveSubscription("premium_monthly") { isActive ->
    if (isActive) {
        enablePremiumFeatures()
    }
}
```

## Troubleshooting

### Logs Not Printing in Release
- Check ProGuard rules are applied
- Verify consumer-rules.pro is included
- Ensure LogLevel is not NONE

### Billing Connection Issues
- Check Google Play Services installed
- Verify app is published (at least internal track)
- Check subscription IDs match Play Console

### Products Not Showing
- Wait for billing connection (automatic)
- Check logs for connection status
- Verify subscription IDs are correct

### Test Purchases
- Use test account in Play Console
- Purchase with test payment method
- Verify in Play Console > Order Management

## Contributing

### Prerequisites
- Android Studio Hedgehog+
- Kotlin 2.0+
- Min SDK 21, Target SDK 36

### Development Setup
1. Clone repo: `git clone https://github.com/shibaprasadsahu/billingkit.git`
2. Open in Android Studio
3. Sync Gradle
4. Run sample app

### Code Style
- Follow Kotlin conventions
- Use `internal` for implementation
- Document public APIs
- Add tests for new features
- Run lint before committing

### Pull Request Process
1. Create feature branch from `main`
2. Write tests for new features
3. Update documentation
4. Run `./gradlew test lint`
5. Submit PR with clear description

## Support

- **Issues**: [GitHub Issues](https://github.com/shibaprasadsahu/billingkit/issues)
- **Email**: shibaprasadsahu943@gmail.com
- **Documentation**: [README](https://github.com/shibaprasadsahu/billingkit#readme)

---

**Last Updated**: 2025-10-09
**Library Version**: 0.1-alpha01
**Billing Library**: 8.0.0
