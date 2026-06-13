package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.ui.theme.BrandGradient

/**
 * Captures a hardware-scanner ("keyboard wedge" / HID mode) scan of the WooCommerce
 * key QR code. The scanner types the decoded text as key events, which we accumulate
 * and hand back. Finalises on Enter, or after a short pause for scanners that send no
 * terminator. No soft keyboard is shown.
 */
@Composable
fun ScanKeysDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var buffer by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }

    fun submit(value: String) {
        if (!done && value.isNotBlank()) {
            done = true
            onResult(value)
        }
    }

    // Keyboard/HID mode: focus the capture surface so scanned keystrokes land here.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Broadcast/intent mode: take the scan from the broadcast receiver.
    LaunchedEffect(Unit) {
        ScannerHub.scans.collect { raw -> submit(raw) }
    }

    // Fallback for scanners that don't append Enter: finalise once input goes quiet.
    LaunchedEffect(buffer) {
        if (buffer.isNotBlank()) {
            delay(500)
            submit(buffer)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BrandGradient)
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    submit(buffer)
                                    true
                                }
                                else -> {
                                    val codePoint = event.utf16CodePoint
                                    if (codePoint != 0 && !Character.isISOControl(codePoint)) {
                                        buffer += codePoint.toChar()
                                    }
                                    true
                                }
                            }
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Scan API key QR code",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Point the scanner at the QR code WooCommerce shows after creating " +
                        "REST API keys, then pull the trigger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (buffer.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Reading… ${buffer.length} characters",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { submit(buffer) },
                        enabled = buffer.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Use")
                    }
                }
            }
        }
    }
}
