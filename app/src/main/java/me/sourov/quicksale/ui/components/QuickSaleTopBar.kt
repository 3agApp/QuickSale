package me.sourov.quicksale.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSaleTopBar(
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
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )

    if (searchEnabled && searchActive) {
        val focusRequester = remember { FocusRequester() }
        // Only steal focus / show the keyboard when the user opened search — not when a
        // scan populated the field.
        LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }
        TopAppBar(
            colors = colors,
            navigationIcon = {
                IconButton(onClick = onSearchClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            },
            title = {
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
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
        )
    } else {
        TopAppBar(
            colors = colors,
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            title = { QuickSaleBrandLockup() },
            actions = {
                if (searchEnabled) {
                    IconButton(onClick = onSearchOpen) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                }
            },
        )
    }
}
