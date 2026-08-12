package me.sourov.quicksale.ui.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import me.sourov.quicksale.data.sync.SyncState
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
    val unpublished by viewModel.unpublishedMatch.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = syncState.isRunning,
        onRefresh = { SyncManager.syncProducts(context) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = syncState.isRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val fraction = (syncState as? SyncState.Running)?.fraction ?: 0f
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The match count used to be a row here; it is the top bar's subtitle now, where it
            // costs nothing. See `barDetail` in QuickSaleApp.

            val refreshing = products.loadState.refresh is LoadState.Loading
            when {
                refreshing && products.itemCount == 0 -> LoadingState()

                // A scanned code that resolves to a product the store hasn't published. Naming it
                // beats "no matches", which reads as a broken scanner or a stale catalog.
                products.itemCount == 0 && unpublished != null -> EmptyState(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Not published",
                    message = "${unpublished?.name} is ${unpublished?.statusLabel} on the store, " +
                        "so it can't be searched or ordered. Publish it on the website, then sync.",
                )

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

                // Dividers, not cards with gaps: 8dp between nine cards is 64dp of nothing, and
                // the till's own cart already reads as a divided list.
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.screen),
                ) {
                    items(
                        count = products.itemCount,
                        key = products.itemKey { it.id },
                    ) { index ->
                        products[index]?.let { product ->
                            ProductRow(product = product, onClick = { onProductClick(product.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One product, in two lines beside its picture.
 *
 * This used to be a card: a 64dp thumbnail beside a three-line stack — name, codes, then a stock
 * badge on a line of its own — with 8dp of gap to the next card, which came to 88–112dp a row and
 * about five and a half rows on screen. The name and price share the top line now and the code and
 * stock badge share the second, which roughly doubles what a counter can see at once.
 *
 * The trailing chevron is gone with it. The whole row is tappable, so it said nothing, and it was
 * forcing the price column to be two elements tall.
 */
@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(product.imageUrl, size = Sizes.thumbnail)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = product.price.asPrice(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The EAN only. Both codes are searchable and both used to be shown, but at this
                // width the SKU was truncated to "SKU B507-O…" on every row — space spent saying
                // nothing. The EAN is what scanners send, and the SKU is on the detail page.
                val code = product.ean.takeIf { it.isNotBlank() }?.let { "EAN $it" }
                    ?: product.sku.takeIf { it.isNotBlank() }?.let { "SKU $it" }
                if (code != null) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(Spacing.sm))
                StockBadge(product)
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

/** A store price string rendered the way the store's own website renders it. */
fun String.asPrice(): String = CurrencyFormatter.format(this)
