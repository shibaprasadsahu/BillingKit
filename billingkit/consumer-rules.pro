# BillingKit Consumer ProGuard Rules

# Public API - Users interact with these
-keep public class com.shibaprasadsahu.billingkit.api.BillingKit {
    public *;
}

# Models - Data classes must be kept
-keep class com.shibaprasadsahu.billingkit.model.** { *; }

# Enums
-keep enum com.shibaprasadsahu.billingkit.LogLevel { *; }

# Listeners - Fun interfaces for callbacks
-keep interface com.shibaprasadsahu.billingkit.listener.** { *; }

# Logger - Critical for logging in release builds
-keep class com.shibaprasadsahu.billingkit.internal.BillingLogger {
    public static *** configure(...);
    public static *** verbose(...);
    public static *** debug(...);
    public static *** info(...);
    public static *** warn(...);
    public static *** error(...);
}

# Kotlin - Minimal required attributes
-keepattributes Signature
