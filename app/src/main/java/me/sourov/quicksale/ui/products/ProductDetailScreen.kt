package me.sourov.quicksale.ui.products

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.settings.LabelSettingsRepository
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.device.label.LabelRenderer
import me.sourov.quicksale.device.printer.BldPrintManager
import me.sourov.quicksale.device.printer.LcPrintDriver
import me.sourov.quicksale.device.printer.NoPrinterDriver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    productId: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember {
        ProductRepository(QuickSaleDatabase.getInstance(context).productDao())
    }
    val labelRenderer = remember { LabelRenderer() }
    val printer = remember {
        if (BldPrintManager.isSupported()) LcPrintDriver(context) else NoPrinterDriver()
    }
    val labelSettingsRepository = remember {
        LabelSettingsRepository(context.applicationContext.settingsDataStore)
    }
    val viewModel: ProductDetailViewModel = viewModel(
        factory = ProductDetailViewModel.factory(
            repository, productId, labelRenderer, printer, labelSettingsRepository,
        ),
    )
    val product by viewModel.product.collectAsStateWithLifecycle()

    val current = product
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Product not found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var showFullImage by remember { mutableStateOf(false) }
    var showPrintSheet by remember { mutableStateOf(false) }
    val hasImage = !current.imageUrl.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        DetailImage(
            imageUrl = current.imageUrl,
            onClick = { if (hasImage) showFullImage = true },
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = current.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current.price.asPrice(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (current.onSale && current.regularPrice.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = current.regularPrice.asPrice(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        StockBadge(current)

        if (current.sku.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            DetailRow(label = "SKU", value = current.sku)
        }

        if (current.ean.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            DetailRow(label = "EAN", value = current.ean)
        }

        // Only worth a row when the store actually restricts the quantity — every other product
        // is simply sold one at a time, which is what an absent row says.
        if (current.minOrderQuantity > 1 || current.orderQuantityStep > 1) {
            Spacer(Modifier.height(8.dp))
            DetailRow(label = "Pack (VE)", value = packSizeSummary(current))
        }

        if (current.categoryList.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                current.categoryList.forEach { category ->
                    AssistChip(onClick = {}, label = { Text(category, maxLines = 1) })
                }
            }
        }

        if (current.description.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ExpandableDescription(current.description)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { showPrintSheet = true },
            enabled = viewModel.hasPrinter,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Print label")
        }
        if (!viewModel.hasPrinter) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This device has no printer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showFullImage && current.imageUrl != null) {
        ZoomableImageDialog(
            imageUrl = current.imageUrl,
            onDismiss = { showFullImage = false },
        )
    }

    if (showPrintSheet) {
        LabelPrintSheet(
            viewModel = viewModel,
            onDismiss = {
                viewModel.consumeMessage()
                showPrintSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelPrintSheet(
    viewModel: ProductDetailViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by viewModel.labelSettings.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val printing by viewModel.printing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val product by viewModel.product.collectAsStateWithLifecycle()

    // The preview already shows the gap, but say why: a label with no barcode reads as a bug
    // otherwise, and the fix is on the store, not in these settings.
    val hint = if (settings.showBarcode && product?.ean.isNullOrBlank()) {
        "This product has no EAN, so its label prints without a barcode."
    } else {
        "Choose which fields print in Settings → Label printing."
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Print label",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            preview?.let { bmp ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Label preview",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    )
                }
            }

            StepperRow(
                label = "Copies",
                value = settings.copies,
                onDecrease = { viewModel.setCopies(settings.copies - 1) },
                onIncrease = { viewModel.setCopies(settings.copies + 1) },
            )
            // On die-cut stock the printer advances to the next label's mark itself, so there is no
            // gap left for anyone to dial in.
            if (!settings.feedsToNextLabel) {
                StepperRow(
                    label = "Spacing",
                    value = settings.spacing,
                    onDecrease = { viewModel.setSpacing(settings.spacing - 1) },
                    onIncrease = { viewModel.setSpacing(settings.spacing + 1) },
                )
            }

            Button(
                onClick = viewModel::print,
                enabled = viewModel.hasPrinter && !printing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (printing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Print")
                }
            }

            Text(
                text = message ?: hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDecrease) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FilledTonalIconButton(onClick = onIncrease) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

/**
 * How the store sells this product, in the two numbers the order screen enforces: the smallest
 * quantity it will take, and how quantities grow above it when the two differ.
 */
private fun packSizeSummary(product: Product): String {
    val min = product.minOrderQuantity.coerceAtLeast(1)
    val step = product.orderQuantityStep.coerceAtLeast(1)
    return if (step == min) "$min" else "$min, then +$step"
}

/**
 * A product description, cut to [COLLAPSED_DESCRIPTION_LINES] with a *Show more* button when it
 * runs longer than that.
 *
 * Wholesale descriptions are routinely a wall of care instructions and packaging notes, and left
 * whole they push the print button — the reason anyone opens this screen — a screenful or two down.
 * The button only appears when the text is actually cut off, so a two-line description keeps its
 * old shape; whether it is cut off is what the layout itself reports, not a guess from the
 * character count, so it stays right at any font scale or screen width.
 */
@Composable
private fun ExpandableDescription(description: String) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    var truncated by remember(description) { mutableStateOf(false) }

    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
        overflow = TextOverflow.Ellipsis,
        // Only meaningful while collapsed: expanded text never overflows, and reading it back then
        // would retract the button that got the reader here.
        onTextLayout = { layout -> if (!expanded) truncated = layout.hasVisualOverflow },
        modifier = Modifier.animateContentSize(),
    )
    if (truncated) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(if (expanded) "Show less" else "Show more")
        }
    }
}

private const val COLLAPSED_DESCRIPTION_LINES = 4

@Composable
private fun DetailImage(imageUrl: String?, onClick: () -> Unit) {
    val hasImage = !imageUrl.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (hasImage) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        if (hasImage) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomOutMap,
                    contentDescription = "Zoom",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
