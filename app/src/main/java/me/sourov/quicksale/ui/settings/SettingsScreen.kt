package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.settings.HTTPS_SITE_URL_PREFIX
import me.sourov.quicksale.data.settings.hasHttpsSiteUrlHost
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.data.sync.SyncEtagRepository
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import me.sourov.quicksale.ui.update.AppUpdateSettingsSection
import me.sourov.quicksale.ui.update.AppUpdateViewModel

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }

    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            repository = container.settings,
            onStoreChanged = {
                SyncEtagRepository(context.applicationContext.settingsDataStore).clear()
                SyncManager.syncAll(context)
            },
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scannerViewModel: ScannerViewModel =
        viewModel(factory = ScannerViewModel.factory(container.scannerConfig))
    val orderSettingsViewModel: OrderSettingsViewModel =
        viewModel(factory = OrderSettingsViewModel.factory(container.orderSettings))
    val labelSettingsViewModel: LabelSettingsViewModel =
        viewModel(factory = LabelSettingsViewModel.factory(container.labelSettings))
    val updateViewModel: AppUpdateViewModel =
        viewModel(factory = AppUpdateViewModel.factory(container.updatePreferences))

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(Unit) {
        updateViewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var secretVisible by rememberSaveable { mutableStateOf(false) }
    var showScanDialog by rememberSaveable { mutableStateOf(false) }

    // Clear focus after a scan so the soft keyboard doesn't pop up on the URL field.
    val focusManager = LocalFocusManager.current
    var scanCount by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scanCount) {
        if (scanCount > 0) focusManager.clearFocus(force = true)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        SectionHeader(
            title = "Store connection",
            subtitle = "Connect QuickSale to your WooCommerce store",
        )

        Spacer(Modifier.height(Spacing.sectionGap))
        ConnectionStatusCard(
            isConfigured = state.saved.isConfigured,
            siteUrl = state.saved.siteUrl,
        )

        Spacer(Modifier.height(Spacing.lg))
        val isIncompleteUrl = !hasHttpsSiteUrlHost(state.siteHost)
        OutlinedTextField(
            value = state.siteHost,
            onValueChange = viewModel::onSiteUrlChange,
            label = { Text("Site URL") },
            placeholder = { Text("yourstore.com") },
            // The scheme is an adornment, not editable text: QuickSale only talks HTTPS, and a
            // prefix the operator can backspace into is a prefix they can corrupt.
            prefix = { Text(HTTPS_SITE_URL_PREFIX) },
            leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            singleLine = true,
            supportingText = if (isIncompleteUrl) {
                {
                    Text(
                        text = "Enter your store's domain, e.g. yourstore.com",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "API keys",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Create them in WooCommerce → Settings → Advanced → REST API. The key's user " +
                "needs the manage_woocommerce capability to read organizations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))

        if (!state.showCredentialFields) {
            CredentialEntryChooser(
                onScan = { showScanDialog = true },
                onManual = viewModel::enterManualEntry,
            )
        } else {
            OutlinedButton(
                onClick = { showScanDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text("Scan QR code")
            }

            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.consumerKey,
                onValueChange = viewModel::onConsumerKeyChange,
                label = { Text("Consumer key") },
                placeholder = { Text("ck_xxxxxxxxxxxx") },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.consumerSecret,
                onValueChange = viewModel::onConsumerSecretChange,
                label = { Text("Consumer secret") },
                placeholder = { Text("cs_xxxxxxxxxxxx") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    val toggleIcon: ImageVector =
                        if (secretVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(
                            imageVector = toggleIcon,
                            contentDescription = if (secretVisible) "Hide secret" else "Show secret",
                        )
                    }
                },
                visualTransformation = if (secretVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state.canTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.connectionTest is ConnectionTestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Testing…")
                } else {
                    Text("Test connection")
                }
            }

            ConnectionTestResult(state.connectionTest)
        }

        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.button),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (state.isDirty) "Save changes" else "Saved")
            }
        }

        SettingsSectionSpacer()
        SyncSettingsSection()

        SettingsSectionSpacer()
        OrderSettingsSection(viewModel = orderSettingsViewModel)

        SettingsSectionSpacer()
        LabelSettingsSection(viewModel = labelSettingsViewModel)

        SettingsSectionSpacer()
        ScannerSettingsSection(viewModel = scannerViewModel)

        SettingsSectionSpacer()
        AppUpdateSettingsSection(viewModel = updateViewModel)

        Spacer(Modifier.height(Spacing.xl))
    }

    if (showScanDialog) {
        ScanKeysDialog(
            onResult = { raw ->
                showScanDialog = false
                viewModel.onCredentialsScanned(raw)
                scanCount++
            },
            onDismiss = { showScanDialog = false },
        )
    }
}

/** The rule + breathing room between two settings sections. */
@Composable
private fun SettingsSectionSpacer() {
    Spacer(Modifier.height(Spacing.sectionSpacing))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun CredentialEntryChooser(
    onScan: () -> Unit,
    onManual: () -> Unit,
) {
    QuickSaleCard {
        Column(Modifier.padding(Spacing.lg)) {
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(Spacing.sm))
                Text("Scan QR code")
            }
            Spacer(Modifier.height(Spacing.sm))
            TextButton(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enter manually")
            }
        }
    }
}

/**
 * The outcome of a connection test. A partial result is its own state: the keys work, but the
 * organization routes didn't answer — which is a plugin problem, not a credentials problem, and
 * saying so saves the operator from re-typing keys that were fine.
 */
@Composable
private fun ConnectionTestResult(state: ConnectionTestState) {
    val (icon, tint, message) = when (state) {
        is ConnectionTestState.Success -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            state.message,
        )

        is ConnectionTestState.Partial -> Triple(
            Icons.Outlined.WarningAmber,
            MaterialTheme.colorScheme.secondary,
            state.message,
        )

        is ConnectionTestState.Failure -> Triple(
            Icons.Outlined.ErrorOutline,
            MaterialTheme.colorScheme.error,
            state.message,
        )

        else -> return
    }
    Spacer(Modifier.height(Spacing.md))
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun ConnectionStatusCard(
    isConfigured: Boolean,
    siteUrl: String,
) {
    val containerColor = if (isConfigured) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val icon = if (isConfigured) Icons.Filled.CheckCircle else Icons.Outlined.Info
    val iconTint: Color = if (isConfigured) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    QuickSaleCard(containerColor = containerColor) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
            Column {
                Text(
                    text = if (isConfigured) "Store connected" else "Not connected",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isConfigured) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = if (isConfigured) siteUrl else "Add your store URL and API keys below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConfigured) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
