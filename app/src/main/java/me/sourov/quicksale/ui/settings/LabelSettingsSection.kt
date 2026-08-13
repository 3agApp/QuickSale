package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.settings.LabelMedia
import me.sourov.quicksale.data.settings.LabelSettings
import kotlin.math.roundToInt

@Composable
fun LabelSettingsSection(
    viewModel: LabelSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Label printing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "The paper in the printer, and what goes on a label.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Paper",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        LabelMedia.entries.forEach { media ->
            MediaRow(
                media = media,
                selected = settings.media == media,
                onSelect = { viewModel.setMedia(media) },
            )
        }

        Spacer(Modifier.height(12.dp))
        DensityRow(
            density = settings.density,
            onChange = viewModel::setDensity,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Fields",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        SwitchRow("Product name", settings.showName, viewModel::setShowName)
        SwitchRow("Brand", settings.showBrand, viewModel::setShowBrand)
        SwitchRow("SKU text", settings.showSku, viewModel::setShowSku)
        SwitchRow("Pack size (VE)", settings.showPackSize, viewModel::setShowPackSize)
        SwitchRow("MSRP (UVP)", settings.showMsrp, viewModel::setShowMsrp)
        SwitchRow("Price (EK)", settings.showPrice, viewModel::setShowPrice)
        SwitchRow("Barcode (EAN)", settings.showBarcode, viewModel::setShowBarcode)
        SwitchRow("EAN number", settings.showEanNumber, viewModel::setShowEanNumber)

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Fields print in this order, and a product missing one simply skips it. The " +
                "barcode is always the product's EAN — never the SKU.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How dark the printer burns, on its own 1–11 scale.
 *
 * The number is shown rather than hidden behind words like "darker": setting this is a matter of
 * printing one label, looking at it and nudging, and a value you can read back is what makes the
 * second attempt an adjustment instead of a guess — and what lets one till be matched to another
 * over the phone.
 *
 * The drag is kept local and only written when the finger lifts, so a slide from one end of the
 * scale to the other is one saved setting rather than ten.
 */
@Composable
private fun DensityRow(density: Int, onChange: (Int) -> Unit) {
    var value by remember(density) { mutableFloatStateOf(density.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Print darkness",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${value.roundToInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = LabelSettings.MIN_DENSITY.toFloat()..LabelSettings.MAX_DENSITY.toFloat(),
            steps = LabelSettings.MAX_DENSITY - LabelSettings.MIN_DENSITY - 1,
        )
        Text(
            text = "Raise this if labels come out grey or the barcode won't scan. Too high and the " +
                "bars bleed into each other, which stops a scan just as surely.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaRow(media: LabelMedia, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(media.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = media.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
