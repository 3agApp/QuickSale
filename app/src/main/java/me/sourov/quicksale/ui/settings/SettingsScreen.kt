package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.settingsDataStore

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context.applicationContext.settingsDataStore) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scannerRepository = remember {
        ScannerConfigRepository(context.applicationContext.settingsDataStore)
    }
    val scannerViewModel: ScannerViewModel =
        viewModel(factory = ScannerViewModel.factory(scannerRepository))

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var secretVisible by rememberSaveable { mutableStateOf(false) }
    var showScanDialog by rememberSaveable { mutableStateOf(false) }

    // Clear focus after a scan so the soft keyboard doesn't pop up on the URL field.
    val focusManager = LocalFocusManager.current
    var scanCount by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(scanCount) {
        if (scanCount > 0) focusManager.clearFocus(force = true)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Store settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Connect QuickSale to your WooCommerce store.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        ConnectionStatusCard(
            isConfigured = state.saved.isConfigured,
            siteUrl = state.saved.siteUrl,
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.siteUrl,
            onValueChange = viewModel::onSiteUrlChange,
            label = { Text("Site URL") },
            placeholder = { Text("https://yourstore.com") },
            leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "API keys",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Create them in WooCommerce → Settings → Advanced → REST API.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.size(8.dp))
                Text("Scan QR code")
            }

            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state.canTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.connectionTest is ConnectionTestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Testing…")
                } else {
                    Text("Test connection")
                }
            }

            ConnectionTestResult(state.connectionTest)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        ScannerSettingsSection(viewModel = scannerViewModel)
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

@Composable
private fun CredentialEntryChooser(
    onScan: () -> Unit,
    onManual: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Scan QR code")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enter manually")
            }
        }
    }
}

@Composable
private fun ConnectionTestResult(state: ConnectionTestState) {
    val (icon, tint, message) = when (state) {
        is ConnectionTestState.Success -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            state.message,
        )
        is ConnectionTestState.Failure -> Triple(
            Icons.Outlined.ErrorOutline,
            MaterialTheme.colorScheme.error,
            state.message,
        )
        else -> return
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
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
    val iconTint = if (isConfigured) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isConfigured) siteUrl else "Add your store URL and API keys below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
