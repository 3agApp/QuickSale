package me.sourov.quicksale.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.CurrencyFormatter
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    query: String,
    onProductClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.products }
    val viewModel: ProductsViewModel = viewModel(factory = ProductsViewModel.factory(repository))

    LaunchedEffect(query) { viewModel.setQuery(query) }

    val products = viewModel.products.collectAsLazyPagingItems()
    val count by viewModel.matchingCount.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = syncState.isRunning,
        onRefresh = { SyncManager.syncProducts(context) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "$count ${if (count == 1) "product" else "products"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.screen,
                    top = Spacing.md,
                    bottom = Spacing.xs,
                ),
            )

            val refreshing = products.loadState.refresh is LoadState.Loading
            when {
                refreshing && products.itemCount == 0 -> LoadingState()

                products.itemCount == 0 && query.isNotBlank() -> EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "No matches",
                    message = "No product matches \"$query\".",
                )

                products.itemCount == 0 -> EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "No products yet",
                    message = "Sync your catalog to scan and sell.",
                    actionLabel = if (syncState.isRunning) null else "Sync now",
                    onAction = { SyncManager.syncProducts(context) },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        bottom = Spacing.screen,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(
                        count = products.itemCount,
                        key = products.itemKey { it.id },
                    ) { index ->
                        products[index]?.let { product ->
                            ProductRow(product = product, onClick = { onProductClick(product.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(product.imageUrl, size = Sizes.thumbnail)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (product.sku.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "SKU ${product.sku}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                StockBadge(product)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.price.asPrice(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ProductThumbnail(imageUrl: String?, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.4f),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun StockBadge(product: Product) {
    val (label, container, content) = when (product.stockStatus) {
        "instock" -> Triple(
            if (product.stockQuantity != null) "In stock · ${product.stockQuantity}" else "In stock",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        "outofstock" -> Triple(
            "Out of stock",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Triple(
            "Backorder",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
        )
    }
}

fun String.asPrice(): String {
    val trimmed = trim()
    return if (trimmed.isBlank()) "—" else "${CurrencyFormatter.symbol}$trimmed"
}
