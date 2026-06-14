package me.sourov.quicksale.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.update.AppRelease
import me.sourov.quicksale.data.update.compareVersionNames

@Composable
fun AppUpdateSettingsSection(
    viewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestUpdate = state.latestAvailableUpdate

    LaunchedEffect(Unit) {
        viewModel.loadVersions()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "App updates",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Installed version ${state.currentVersionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { viewModel.checkLatest() },
                enabled = !state.isCheckingLatest,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isCheckingLatest) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Check latest")
            }
            Button(
                onClick = { latestUpdate?.let { openUpdateUrl(context, it) } },
                enabled = latestUpdate != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = latestUpdate?.let { "Update ${it.versionName}" } ?: "Up to date",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.isLoadingVersions) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Loading release versions...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.releases.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Specific version",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            state.releases.take(12).forEachIndexed { index, release ->
                if (index > 0) HorizontalDivider()
                ReleaseVersionRow(
                    release = release,
                    currentVersionName = state.currentVersionName,
                    onOpen = { openUpdateUrl(context, release) },
                )
            }
        }
    }
}

@Composable
private fun ReleaseVersionRow(
    release: AppRelease,
    currentVersionName: String,
    onOpen: () -> Unit,
) {
    val isCurrent = compareVersionNames(release.versionName, currentVersionName) == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.versionName.ifBlank { release.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    isCurrent -> "Current version"
                    release.apkDownloadUrl != null -> "APK available"
                    else -> "Release page"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onOpen) {
            Text(if (isCurrent) "Open" else "Update")
        }
    }
}
