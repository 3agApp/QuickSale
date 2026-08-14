package me.sourov.quicksale.ui.print

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.device.label.LabelRenderer
import me.sourov.quicksale.device.printer.BldPrintManager
import me.sourov.quicksale.device.printer.LcPrintDriver
import me.sourov.quicksale.device.printer.NoPrinterDriver
import me.sourov.quicksale.ui.components.IconBadge
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.RepeatingStepperButton
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scan, print, repeat.
 *
 * Labelling a delivery is one action done a hundred times, so this screen removes every step
 * between the trigger and the paper: a scan that names exactly one product prints it, and the next
 * scan prints the next one. There is nothing to tap in the common case.
 *
 * The screen collects scans itself rather than the view model doing it, because a bottom-bar tab's
 * view model outlives the tab you can see — and a label printing while somebody is on the checkout
 * would be a genuinely bad surprise.
 */
@Composable
fun QuickPrintScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val labelRenderer = remember { LabelRenderer() }
    val printer = remember(context) {
        if (BldPrintManager.isSupported()) LcPrintDriver(context) else NoPrinterDriver()
    }
    val viewModel: QuickPrintViewModel = viewModel(
        factory = QuickPrintViewModel.factory(
            repository = container.products,
            labelRenderer = labelRenderer,
            printer = printer,
            labelSettingsRepository = container.labelSettings,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.labelSettings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val lastPrinted by viewModel.lastPrinted.collectAsStateWithLifecycle()

    var typedCode by remember { mutableStateOf("") }
    // The controls and the tally are both things you set once and then work past, so they start
    // folded away: the status is what this screen is for and it should own the screen.
    var showControls by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showTypedCode by remember { mutableStateOf(false) }

    // Only while this tab is actually on screen — see the note on the function.
    LaunchedEffect(Unit) {
        ScannerHub.scans.collect { scan -> viewModel.onCode(scan) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The typed-code field sits low on this page; the keyboard must push it into view.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        if (!viewModel.hasPrinter) {
            NoPrinterNotice()
            Spacer(Modifier.height(Spacing.lg))
        }

        StatusCard(
            state = state,
            copies = settings.copies,
            onPrint = viewModel::print,
            onDismiss = viewModel::dismiss,
        )

        // Reprinting is the single most common thing to want after a print: label stock jams and
        // streaks, and the box has usually already gone back on the pallet by the time you notice.
        lastPrinted?.let { product ->
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(
                onClick = viewModel::reprintLast,
                enabled = state !is QuickPrintState.Printing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Print ${product.name} again",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        SettingsDisclosure(
            summary = settingsSummary(settings),
            expanded = showControls,
            onToggle = { showControls = !showControls },
        )
        if (showControls) {
            Spacer(Modifier.height(Spacing.sm))
            QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                LabelStepper(
                    label = "Copies per scan",
                    value = settings.copies,
                    min = LabelSettings.MIN_COPIES,
                    max = LabelSettings.MAX_COPIES,
                    onChange = viewModel::setCopies,
                )
                // On die-cut stock the printer advances to the next label's mark itself, so there
                // is no gap left for anyone to dial in — the same reason the product screen's print
                // sheet hides it. Change the stock in Settings → Label printing.
                if (!settings.feedsToNextLabel) {
                    LabelStepper(
                        label = "Spacing",
                        value = settings.spacing,
                        min = LabelSettings.MIN_SPACING,
                        max = LabelSettings.MAX_SPACING,
                        onChange = viewModel::setSpacing,
                    )
                }
                Text(
                    text = if (settings.feedsToNextLabel) {
                        "Every scan prints this many. Choose which fields print in " +
                            "Settings → Label printing."
                    } else {
                        "Every scan prints this many labels, with that many blank lines fed " +
                            "after each. Choose which fields print in Settings → Label printing."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }

        // Folded away, like the controls above it. A 56dp field plus its "For a barcode too
        // damaged to scan" caption is ~80dp standing permanently between the status card and the
        // tally, for the rarest thing this screen does — the scanner is the point of the device.
        Spacer(Modifier.height(Spacing.sm))
        if (showTypedCode) {
            OutlinedTextField(
                value = typedCode,
                onValueChange = { typedCode = it },
                label = { Text("Code of a barcode too damaged to scan") },
                placeholder = { Text("EAN or SKU") },
                leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        viewModel.onCode(typedCode)
                        typedCode = ""
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showTypedCode = true }) { Text("Type a code instead") }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Printed this session",
                subtitle = "${count(history.sumOf { it.copies }, "label")} from " +
                    count(history.size, "scan"),
                trailing = {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if (showHistory) "Hide" else "Show")
                    }
                },
            )
            if (showHistory) {
                Spacer(Modifier.height(Spacing.sectionGap))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    history.forEach { entry -> HistoryRow(entry) }
                }
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = viewModel::clearHistory) { Text("Clear the tally") }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** One line describing how the printer is set, and the way to open the controls that change it. */
@Composable
private fun SettingsDisclosure(
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (expanded) "Done" else "Change",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun settingsSummary(settings: LabelSettings): String = buildString {
    append(count(settings.copies, "copy", "copies"))
    append(" per scan")
    if (!settings.feedsToNextLabel) append(" · spacing ${settings.spacing}")
}

/**
 * The one thing worth looking at: what the last scan did.
 *
 * It is a whole card rather than a snackbar because this screen is used at arm's length, with a
 * device in one hand and a box in the other, and a message that fades after four seconds is a
 * message that gets missed.
 */
@Composable
private fun StatusCard(
    state: QuickPrintState,
    copies: Int,
    onPrint: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        QuickPrintState.Waiting -> Banner(
            icon = Icons.Outlined.QrCodeScanner,
            container = colors.surfaceContainer,
            content = colors.onSurface,
            title = "Ready to scan",
            body = if (copies == 1) {
                "Scan a barcode and its label prints straight away."
            } else {
                "Scan a barcode and $copies labels print straight away."
            },
        )

        is QuickPrintState.Printing -> Banner(
            icon = Icons.Filled.Print,
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            title = "Printing…",
            body = state.product.name,
            busy = true,
        )

        is QuickPrintState.Printed -> Banner(
            icon = Icons.Outlined.CheckCircle,
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            title = if (state.copies == 1) "Printed" else "Printed ${state.copies} labels",
            body = state.product.name,
        )

        is QuickPrintState.NoMatch -> Banner(
            icon = Icons.Outlined.SearchOff,
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            title = "Nothing matches that code",
            body = "\"${state.code}\" isn't an EAN or SKU in the synced catalog. " +
                "Sync products if it's new to the store.",
        )

        // A draft is a real product on a real shelf, so its label is worth printing — but not by
        // surprise, since a draft usually means somebody isn't finished with it yet.
        is QuickPrintState.ConfirmUnpublished -> Banner(
            icon = Icons.Outlined.WarningAmber,
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            title = "${state.product.name} is ${state.product.statusLabel} on the store",
            body = "It can't be sold until it's published. Print its label anyway?",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = { onPrint(state.product) }) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Print anyway")
                }
                OutlinedButton(onClick = onDismiss) { Text("Skip") }
            }
        }

        is QuickPrintState.Ambiguous -> Banner(
            icon = Icons.Outlined.WarningAmber,
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            title = "${state.matches.size} products share that code",
            body = "Nothing printed. Tap the one you meant.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.matches.forEach { product ->
                    MatchRow(product = product, onClick = { onPrint(product) })
                }
                TextButton(onClick = onDismiss) { Text("None of these") }
            }
        }

        is QuickPrintState.Failed -> Banner(
            icon = Icons.Outlined.ErrorOutline,
            container = colors.errorContainer,
            content = colors.onErrorContainer,
            title = "The printer refused",
            body = state.message,
        ) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    container: Color,
    content: Color,
    title: String,
    body: String,
    busy: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    QuickSaleCard(containerColor = container) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizes.iconLarge),
                        strokeWidth = 2.dp,
                        color = content,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(Sizes.iconLarge),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                )
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.85f),
                )
            }
            actions?.let {
                Spacer(Modifier.height(Spacing.md))
                it()
            }
        }
    }
}

/** One candidate when a code turned out to name more than one product. */
@Composable
private fun MatchRow(product: Product, onClick: () -> Unit) {
    QuickSaleCard(
        modifier = Modifier.clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(product.imageUrl, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = product.name.ifBlank { "(no name)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Which field actually matched is the thing that tells them apart.
                    text = codeSummary(product),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.Print,
                contentDescription = "Print ${product.name}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: PrintedLabel) {
    QuickSaleCard {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(entry.product.imageUrl, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.product.name.ifBlank { "(no name)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${timeFormat.format(Date(entry.atMillis))} · ${entry.product.price.asPrice()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.copies > 1) {
                Text(
                    text = "×${entry.copies}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The print settings this screen edits, held to the same bounds the repository clamps to. */
@Composable
private fun LabelStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingStepperButton(
                onStep = { onChange(value - 1) },
                contentDescription = "Decrease $label",
                enabled = value > min,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            RepeatingStepperButton(
                onStep = { onChange(value + 1) },
                contentDescription = "Increase $label",
                enabled = value < max,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@Composable
private fun NoPrinterNotice() {
    QuickSaleCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Outlined.ErrorOutline,
                size = Sizes.avatarSmall,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "This device has no built-in printer, so nothing here will print.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** [plural] is only needed where adding an "s" would be wrong — "copy" is the one here. */
private fun count(value: Int, singular: String, plural: String = "${singular}s"): String =
    "$value ${if (value == 1) singular else plural}"

private fun codeSummary(product: Product): String = listOfNotNull(
    product.ean.takeIf { it.isNotBlank() }?.let { "EAN $it" },
    product.sku.takeIf { it.isNotBlank() }?.let { "SKU $it" },
).joinToString(" · ").ifBlank { "No code" }

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
