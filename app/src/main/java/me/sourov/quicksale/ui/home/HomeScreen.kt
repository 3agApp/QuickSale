package me.sourov.quicksale.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.CustomerRepository
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncMetaRepository
import me.sourov.quicksale.data.sync.SyncState
import me.sourov.quicksale.navigation.TopLevelDestination

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext.settingsDataStore) }
    val productRepository = remember { ProductRepository(QuickSaleDatabase.getInstance(context).productDao()) }
    val customerRepository = remember { CustomerRepository(QuickSaleDatabase.getInstance(context).customerDao()) }
    val syncMetaRepository = remember { SyncMetaRepository(context.applicationContext.settingsDataStore) }

    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = StoreSettings())
    val productCount by productRepository.count().collectAsStateWithLifecycle(initialValue = 0)
    val customerCount by customerRepository.count().collectAsStateWithLifecycle(initialValue = 0)
    val lastSync by syncMetaRepository.lastSyncMillis.collectAsStateWithLifecycle(initialValue = 0L)
    val syncState by SyncManager.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        HomeHero(settings = settings, onClick = { onNavigate(TopLevelDestination.SETTINGS) })

        Spacer(Modifier.height(16.dp))
        SyncCard(
            syncState = syncState,
            productCount = productCount,
            customerCount = customerCount,
            lastSyncMillis = lastSync,
            onSync = { SyncManager.sync(context) },
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(
                title = "Products",
                subtitle = "Browse catalog",
                icon = Icons.Filled.Inventory2,
                onClick = { onNavigate(TopLevelDestination.PRODUCTS) },
                modifier = Modifier.weight(1f),
            )
            QuickActionCard(
                title = "Customers",
                subtitle = "Find a customer",
                icon = Icons.Filled.People,
                onClick = { onNavigate(TopLevelDestination.CUSTOMERS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SyncCard(
    syncState: SyncState,
    productCount: Int,
    customerCount: Int,
    lastSyncMillis: Long,
    onSync: () -> Unit,
) {
    val isRunning = syncState is SyncState.Running
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Catalog & customers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$productCount products · $customerCount customers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (val state = syncState) {
                is SyncState.Running -> {
                    LinearProgressIndicator(
                        progress = { state.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is SyncState.Error -> StatusLine(
                    icon = Icons.Outlined.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    text = state.message,
                )

                is SyncState.Success -> StatusLine(
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.tertiary,
                    text = "Synced ${state.productCount} products and ${state.customerCount} customers",
                )

                SyncState.Idle -> Text(
                    text = lastSyncLabel(lastSyncMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSync,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (isRunning) "Syncing…" else "Sync now")
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

private fun lastSyncLabel(millis: Long): String {
    if (millis <= 0L) return "Never synced"
    val relative = DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "Last synced $relative"
}

@Composable
private fun HomeHero(
    settings: StoreSettings,
    onClick: () -> Unit,
) {
    val configured = settings.isConfigured
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(me.sourov.quicksale.ui.theme.BrandGradient)
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Column {
            Text(
                text = "Point of sale",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (configured) "You're all set" else "Let's get set up",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (configured) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (configured) {
                        "Connected · ${settings.siteUrl.toDisplayHost()}"
                    } else {
                        "Tap to connect your store"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun String.toDisplayHost(): String =
    trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .height(132.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {}
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
