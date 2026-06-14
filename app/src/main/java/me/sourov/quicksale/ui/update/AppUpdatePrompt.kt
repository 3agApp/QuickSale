package me.sourov.quicksale.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import me.sourov.quicksale.data.update.AppRelease

@Composable
fun AppUpdatePrompt(
    release: AppRelease,
    currentVersionName: String,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit,
    onUpdateOpened: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            Text(
                text = "QuickSale ${release.versionName} is available. " +
                    "You are using $currentVersionName.",
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (openUpdateUrl(context, release)) {
                        onUpdateOpened()
                    }
                },
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) {
                    Text("Later")
                }
                TextButton(onClick = onSkipVersion) {
                    Text("Skip this version")
                }
            }
        },
    )
}
