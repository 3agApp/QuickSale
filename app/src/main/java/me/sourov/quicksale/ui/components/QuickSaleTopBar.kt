package me.sourov.quicksale.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The app bar for every top-level tab.
 *
 * Not Material's `TopAppBar`: that is a fixed 64dp, and on a 775dp screen a bar of that height
 * carrying only a logo cost more than a row of catalog. This is a 48dp strip showing what the tab
 * is actually about — the cart's customer, how many products matched — supplied by the shell
 * through [title]. The wordmark it replaced now lives on the two screens where "what app is this"
 * is a real question: first-run device setup, and the Settings directory.
 *
 * It still has two faces: the title with its actions, and a full-width search field. The search
 * face is allowed to grow past 48dp — a text field has a comfortable height of its own, and search
 * is transient — which is why the height here is a minimum rather than a fixed size.
 *
 * [onSync], when supplied, puts that tab's sync one tap from whatever the operator is looking at.
 * [actions] is for buttons that belong to the tab rather than the app, like the cart's own.
 */
@Composable
fun QuickSaleTopBar(
    title: @Composable RowScope.() -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    searchActive: Boolean,
    autoFocus: Boolean,
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSync: (() -> Unit)? = null,
    syncing: Boolean = false,
    onSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopBarSurface {
        if (searchEnabled && searchActive) {
            val focusRequester = remember { FocusRequester() }
            // Only steal focus / show the keyboard when the user opened search — not when a
            // scan populated the field.
            LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

            IconButton(onClick = onSearchClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        } else {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showBack) Spacing.xs else Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                content = title,
            )
            actions()
            onSync?.let { SyncIconButton(syncing = syncing, onClick = it) }
            if (searchEnabled) {
                IconButton(onClick = onSearchOpen) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
            }
            onSettings?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }
    }
}

/**
 * The bar's shell: full-bleed colour behind the status bar, content inset below it.
 *
 * The inset is applied here because this replaced `TopAppBar`, which was consuming `statusBars`
 * on its own — without it the title sits under the clock.
 */
@Composable
private fun TopBarSurface(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = Sizes.topBar)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * The standard shape of a bar title: what the screen is, and one quiet fact about it.
 *
 * [detail] is what the screen would otherwise spend a whole row saying — "2805", the person the
 * cart belongs to — set beside the name rather than under it. Both sides can shrink, so a long
 * company name and a long person's name share the width instead of one pushing the other out.
 */
@Composable
fun RowScope.TopBarTitle(text: String, detail: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
    detail?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = "  ·  $it",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
