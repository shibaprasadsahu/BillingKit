package com.shibaprasadsahu.billingkit.sample

import android.app.Application
import com.shibaprasadsahu.billingkit.LogLevel
import com.shibaprasadsahu.billingkit.api.BillingKit

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize BillingKit singleton with your subscription product IDs
        BillingKit.initialize(
            context = this,
            subscriptionIds = listOf(
                "premium_monthly",    // Replace with your actual product IDs
                "premium_yearly",     // from Google Play Console
                "pro_monthly"
            ),
            logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        )
    }
}
