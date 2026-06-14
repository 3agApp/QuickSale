package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.scanner.ScannerMode
import me.sourov.quicksale.data.scanner.ScannerPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerSettingsSection(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val diagnostic by viewModel.lastDiagnostic.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Scanner",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "How the handheld scanner sends data to QuickSale.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = config.mode == ScannerMode.KEYBOARD,
                onClick = { viewModel.setMode(ScannerMode.KEYBOARD) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Keyboard") }
            SegmentedButton(
                selected = config.mode == ScannerMode.BROADCAST,
                onClick = { viewModel.setMode(ScannerMode.BROADCAST) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Broadcast") }
        }

        Spacer(Modifier.height(12.dp))

        when (config.mode) {
            ScannerMode.KEYBOARD -> {
                InfoCard(
                    "Set the scanner to Keyboard / HID / Wedge mode. Open the scan dialog, " +
                        "then scan — the code is typed straight in.",
                )
            }

            ScannerMode.BROADCAST -> {
                var expanded by remember { mutableStateOf(false) }
                val selectedPreset = ScannerPreset.fromId(config.presetId)

                InfoCard(
                    "Set your device's scanner app to Broadcast / Intent output mode (not " +
                        "Keyboard/HID). Leave the preset on Auto-detect — if nothing scans, scan " +
                        "once below and match the action shown in \"Last scan received\".",
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedPreset.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Device preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        ScannerPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.displayName) },
                                onClick = {
                                    viewModel.setPreset(preset)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                if (selectedPreset != ScannerPreset.AUTO_DETECT) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.action,
                        onValueChange = viewModel::setAction,
                        label = { Text("Broadcast action") },
                        placeholder = { Text("e.g. com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.extraKey,
                        onValueChange = viewModel::setExtraKey,
                        label = { Text("Data extra key (optional)") },
                        placeholder = { Text("e.g. data — leave blank to read any field") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Last scan received",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        val diag = diagnostic
                        if (diag == null) {
                            Text(
                                text = "Scan a code while on this screen — its broadcast " +
                                    "action and data fields will show here so you can match it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "action: ${diag.action}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            diag.extras.forEach { (key, value) ->
                                Text(
                                    text = "$key: ${value.take(80)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
