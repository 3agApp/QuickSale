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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
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
import me.sourov.quicksale.data.sync.SyncTarget
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
    val productsSync by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()
    val customersSync by SyncManager.state(SyncTarget.Customers).collectAsStateWithLifecycle()
    val productsLastSync by syncMetaRepository.lastSyncMillis(SyncTarget.Products)
        .collectAsStateWithLifecycle(initialValue = 0L)
    val customersLastSync by syncMetaRepository.lastSyncMillis(SyncTarget.Customers)
        .collectAsStateWithLifecycle(initialValue = 0L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        HomeHero(settings = settings, onClick = { onNavigate(TopLevelDestination.SETTINGS) })

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Sync",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        SyncCard(
            productsSync = productsSync,
            customersSync = customersSync,
            productCount = productCount,
            customerCount = customerCount,
            productsLastSync = productsLastSync,
            customersLastSync = customersLastSync,
            onSyncProducts = { SyncManager.syncProducts(context) },
            onSyncCustomers = { SyncManager.syncCustomers(context) },
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
    productsSync: SyncState,
    customersSync: SyncState,
    productCount: Int,
    customerCount: Int,
    productsLastSync: Long,
    customersLastSync: Long,
    onSyncProducts: () -> Unit,
    onSyncCustomers: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SyncRow(
                icon = Icons.Filled.Inventory2,
                title = "Products",
                unit = "products",
                count = productCount,
                state = productsSync,
                lastSyncMillis = productsLastSync,
                onSync = onSyncProducts,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            SyncRow(
                icon = Icons.Filled.People,
                title = "Customers",
                unit = "customers",
                count = customerCount,
                state = customersSync,
                lastSyncMillis = customersLastSync,
                onSync = onSyncCustomers,
            )
        }
    }
}

@Composable
private fun SyncRow(
    icon: ImageVector,
    title: String,
    unit: String,
    count: Int,
    state: SyncState,
    lastSyncMillis: Long,
    onSync: () -> Unit,
) {
    val isRunning = state is SyncState.Running
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SyncStatusText(state = state, unit = unit, count = count, lastSyncMillis = lastSyncMillis)
            }
            Spacer(Modifier.size(12.dp))
            FilledTonalIconButton(onClick = onSync, enabled = !isRunning) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync $title", modifier = Modifier.size(20.dp))
                }
            }
        }

        if (state is SyncState.Running) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SyncStatusText(state: SyncState, unit: String, count: Int, lastSyncMillis: Long) {
    val (text, color) = when (state) {
        is SyncState.Running -> state.message to MaterialTheme.colorScheme.onSurfaceVariant
        is SyncState.Error -> state.message to MaterialTheme.colorScheme.error
        else -> "$count $unit · ${lastSyncLabel(lastSyncMillis)}" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
