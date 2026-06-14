package me.sourov.quicksale.ui.products

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.data.settings.LabelSettingsRepository
import me.sourov.quicksale.device.label.LabelRenderer
import me.sourov.quicksale.device.printer.PrintResult
import me.sourov.quicksale.device.printer.PrinterDriver

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModel(
    repository: ProductRepository,
    productId: Long,
    private val labelRenderer: LabelRenderer,
    private val printer: PrinterDriver,
    private val labelSettingsRepository: LabelSettingsRepository,
) : ViewModel() {

    val product: StateFlow<Product?> = repository.product(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether this device has a built-in printer. */
    val hasPrinter: Boolean = printer.isAvailable

    val labelSettings: StateFlow<LabelSettings> = labelSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabelSettings())

    /** Live label preview (the exact bitmap that prints), rendered off the main thread. */
    val preview: StateFlow<Bitmap?> = combine(product, labelSettings) { p, s -> p to s }
        .mapLatest { (p, s) ->
            if (p == null) null else withContext(Dispatchers.Default) { labelRenderer.render(p, s) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _printing = MutableStateFlow(false)
    val printing: StateFlow<Boolean> = _printing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setCopies(value: Int) {
        viewModelScope.launch { labelSettingsRepository.setCopies(value) }
    }

    fun setSpacing(value: Int) {
        viewModelScope.launch { labelSettingsRepository.setSpacing(value) }
    }

    fun print() {
        val p = product.value ?: return
        if (!printer.isAvailable) {
            _message.value = "This device has no printer"
            return
        }
        val settings = labelSettings.value
        viewModelScope.launch {
            _printing.value = true
            val result = withContext(Dispatchers.Default) {
                printer.printBitmap(labelRenderer.render(p, settings), settings.copies, settings.spacing)
            }
            _printing.value = false
            _message.value = when (result) {
                is PrintResult.Success ->
                    "Printed ${settings.copies} label${if (settings.copies > 1) "s" else ""}"
                is PrintResult.Error -> result.message
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(
            repository: ProductRepository,
            productId: Long,
            labelRenderer: LabelRenderer,
            printer: PrinterDriver,
            labelSettingsRepository: LabelSettingsRepository,
        ) = viewModelFactory {
            initializer {
                ProductDetailViewModel(repository, productId, labelRenderer, printer, labelSettingsRepository)
            }
        }
    }
}
