package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.settings.BackorderRepository
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Whether the till may sell past the shop's count.
 *
 * The explanatory line under the switch changes with it, because the two settings lead to different
 * days: one where a short line is a note on the receipt, and one where it stops the order at the
 * counter. Saying which of those is switched on beats naming the setting twice.
 */
@Composable
fun StockSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.backorders }
    val scope = rememberCoroutineScope()
    val allowed by repository.allowed
        .collectAsStateWithLifecycle(initialValue = BackorderRepository.DEFAULT_ALLOWED)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Stock",
            subtitle = "What happens when an order goes past what the shop has.",
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.lg)) {
                SettingsSwitchRow(
                    title = "Allow backorders",
                    subtitle = "Sell more than the shop has in stock",
                    checked = allowed,
                    onCheckedChange = { value -> scope.launch { repository.setAllowed(value) } },
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = if (allowed) {
                        "Short lines are marked on the order and can still be sold — the count on " +
                            "the shelf runs negative and the store fills the rest later."
                    } else {
                        "A product the shop has run out of can't be added, and the order can't be " +
                            "placed until the short line comes down to what's in stock."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
