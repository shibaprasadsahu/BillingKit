package com.shibaprasadsahu.billingkit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.shibaprasadsahu.billingkit.model.PurchaseResult
import com.shibaprasadsahu.billingkit.model.SubscriptionDetails

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BillingKitTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SubscriptionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SubscriptionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val billingKit = remember { com.shibaprasadsahu.billingkit.api.BillingKit.getInstance() }

    // Observe subscriptions Flow - automatically updates when data changes
    val subscriptions by billingKit.subscriptionsFlow.collectAsState()

    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Set up purchase listener with lifecycle awareness
    // This will:
    // - Immediately query purchases and call the listener
    // - Automatically query purchases on ON_RESUME (when activity resumes)
    // - Always call the listener, even with empty list
    // - Automatically cleanup when activity is destroyed (no manual removal needed)
    DisposableEffect(lifecycleOwner) {
        billingKit.setPurchaseUpdateListener(lifecycleOwner) { owner, purchases ->
            // Handle purchase updates (always called, even with empty list)
            // owner = lifecycle owner (Activity) for lifecycle-aware operations
            statusMessage = if (purchases.isEmpty()) {
                "No active purchases"
            } else {
                "Purchases updated: ${purchases.size} active purchases"
            }
        }

        onDispose {
            // Optional: Manual cleanup (auto-cleanup happens on ON_DESTROY)
            // billingKit.removePurchaseUpdateListener()
        }
    }

    // Enable lifecycle-aware product fetching
    // This will fetch products on ON_START
    DisposableEffect(lifecycleOwner) {
        billingKit.fetchProducts(lifecycleOwner)
        onDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "BillingKit Sample",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SubscriptionList(
                subscriptions = subscriptions,
                statusMessage = statusMessage,
                onSubscribe = { subscription ->
                    // Simplified subscribe - only needs productId!
                    billingKit.subscribe(
                        activity = context as ComponentActivity,
                        productId = subscription.productId
                    ) { result ->
                        when (result) {
                            is PurchaseResult.Success -> {
                                statusMessage = "Successfully subscribed!"
                            }
                            is PurchaseResult.Error -> {
                                statusMessage = "Error: ${result.message}"
                            }
                            is PurchaseResult.Cancelled -> {
                                statusMessage = "Purchase cancelled"
                            }
                            is PurchaseResult.AlreadyOwned -> {
                                statusMessage = "Already subscribed"
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SubscriptionList(
    subscriptions: List<SubscriptionDetails>,
    statusMessage: String?,
    onSubscribe: (SubscriptionDetails) -> Unit
) {
    Column {
        if (statusMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            text = "Available Subscriptions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subscriptions) { subscription ->
                SubscriptionCard(
                    subscription = subscription,
                    onSubscribe = { onSubscribe(subscription) }
                )
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    subscription: SubscriptionDetails,
    onSubscribe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (subscription.isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscription.productTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subscription.isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "ACTIVE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subscription.productDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = subscription.formattedPrice,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "per ${getPeriodText(subscription.regularPhase.billingPeriod)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Free trial or intro offer
            if (subscription.hasFreeTrial) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${subscription.freeTrialDays} days free trial",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            } else if (subscription.hasIntroductoryPrice) {
                Spacer(modifier = Modifier.height(8.dp))
                subscription.introductoryPhase?.let { intro ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Intro: ${intro.formattedPrice}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                enabled = !subscription.isActive
            ) {
                Text(if (subscription.isActive) "Subscribed" else "Subscribe")
            }
        }
    }
}

@Composable
fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error loading subscriptions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun getPeriodText(billingPeriod: String): String {
    return when {
        billingPeriod.contains("P1M") -> "month"
        billingPeriod.contains("P1Y") -> "year"
        billingPeriod.contains("P1W") -> "week"
        billingPeriod.contains("P7D") -> "week"
        else -> "period"
    }
}
