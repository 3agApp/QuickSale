package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.ui.theme.Spacing
import me.sourov.quicksale.ui.update.AppUpdateSettingsSection
import me.sourov.quicksale.ui.update.AppUpdateViewModel

/**
 * One page of settings. Each section keeps the layout it already had — the split is in how you
 * reach it, not in what it shows.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Store connection puts the site URL and both API keys near the bottom of this scroll,
            // which is where the keyboard would otherwise land.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        when (section) {
            SettingsSection.DEVICE -> DeviceModeSection()

            SettingsSection.STORE -> StoreConnectionSection(snackbarHostState)

            SettingsSection.SYNC -> SyncSettingsSection()

            SettingsSection.LABELS -> LabelSettingsSection(
                viewModel = viewModel(
                    factory = LabelSettingsViewModel.factory(container.labelSettings),
                ),
            )

            SettingsSection.SCANNER -> ScannerSettingsSection(
                viewModel = viewModel(
                    factory = ScannerViewModel.factory(container.scannerConfig),
                ),
            )

            SettingsSection.UPDATES -> {
                val updateViewModel: AppUpdateViewModel = viewModel(
                    factory = AppUpdateViewModel.factory(container.updatePreferences),
                )
                LaunchedEffect(Unit) {
                    updateViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }
                AppUpdateSettingsSection(viewModel = updateViewModel, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}
