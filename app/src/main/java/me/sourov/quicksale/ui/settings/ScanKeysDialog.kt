package me.sourov.quicksale.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.scan.QrCaptureActivity
import me.sourov.quicksale.ui.theme.BrandGradient

/**
 * Captures a WooCommerce key QR scan from three sources, applying the first one automatically:
 *  - a hardware scanner in keyboard/HID mode (captured key events, finalised on Enter or a pause),
 *  - a hardware scanner in broadcast mode (via [ScannerHub]),
 *  - the device camera (ZXing), for phones without a hardware scanner.
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

    val cameraLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { submit(it) }
    }

    fun launchCamera() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Point the camera at the API key QR code")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(QrCaptureActivity::class.java)
        cameraLauncher.launch(options)
    }

    // Keyboard/HID mode: focus the capture surface so scanned keystrokes land here.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Broadcast/intent mode: take the scan from the broadcast receiver.
    LaunchedEffect(Unit) { ScannerHub.scans.collect { raw -> submit(raw) } }

    // Fallback for HID scanners that don't append Enter: finalise once input goes quiet.
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
                    text = if (buffer.isEmpty()) {
                        "Pull the trigger on your scanner, or scan with the camera."
                    } else {
                        "Reading… ${buffer.length} characters"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (buffer.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { launchCamera() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("Scan with camera")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
