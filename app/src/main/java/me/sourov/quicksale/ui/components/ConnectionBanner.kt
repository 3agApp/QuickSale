package me.sourov.quicksale.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The one piece of the old Home screen worth keeping, reduced to the case that is actually urgent.
 *
 * Home spent the app's start destination telling the operator that everything was fine. This says
 * nothing at all until something is wrong — the store was never connected, or the last sync
 * failed — and then says it on whichever tab the operator is already on.
 */
@Composable
fun ConnectionBanner(
    configured: Boolean,
    syncError: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = true,
) {
    val problem = remember(configured, syncError, online) {
        when {
            !configured -> Problem(
                headline = "Store not connected",
                detail = "Tap to add your site address and API keys.",
                fatal = true,
            )
            // Worth saying because it changes what the till can do: the catalog and the accounts
            // are on the device and keep working, but an order needs the store to accept it, so
            // placing one now will fail rather than wait.
            !online -> Problem(
                headline = "Offline",
                detail = "Scanning, searching and printing still work. Orders can't be placed.",
                fatal = false,
            )
            syncError != null -> Problem(
                headline = "Last sync failed",
                detail = syncError,
                fatal = false,
            )
            else -> null
        }
    }

    // Retained after the problem clears so the collapse animation still has something to draw.
    // Written from an effect rather than during composition: a composition-phase write of a
    // freshly built value recomposes forever, which is exactly what it did.
    var shown by remember { mutableStateOf<Problem?>(null) }
    LaunchedEffect(problem) { if (problem != null) shown = problem }

    AnimatedVisibility(
        visible = problem != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val shown = shown ?: return@AnimatedVisibility
        val container = if (shown.fatal) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val content = if (shown.fatal) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

        Surface(color = container, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (shown.fatal) Icons.Outlined.Info else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shown.headline,
                        style = MaterialTheme.typography.titleSmall,
                        color = content,
                    )
                    Text(
                        text = shown.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A data class so an unchanged problem compares equal and doesn't retrigger the effect above. */
private data class Problem(val headline: String, val detail: String, val fatal: Boolean)
