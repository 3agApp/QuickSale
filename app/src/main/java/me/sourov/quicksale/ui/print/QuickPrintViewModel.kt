package me.sourov.quicksale.ui.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Where the last scan got to. The screen is a view of this and nothing else. */
sealed interface QuickPrintState {
    /** Nothing scanned yet this session. */
    data object Waiting : QuickPrintState

    data class Printing(val product: Product) : QuickPrintState

    data class Printed(val product: Product, val copies: Int) : QuickPrintState

    /** The catalog has nothing under that code. */
    data class NoMatch(val code: String) : QuickPrintState

    /**
     * The code found a product the store hasn't published. It still prints — a label identifies
     * stock on a shelf rather than selling it — but not without being told so first.
     */
    data class ConfirmUnpublished(val product: Product) : QuickPrintState

    /** The code named more than one product, so the operator picks rather than the app guessing. */
    data class Ambiguous(val code: String, val matches: List<Product>) : QuickPrintState

    data class Failed(val message: String) : QuickPrintState
}

/** One label that came out of the printer, for the running tally on screen. */
data class PrintedLabel(
    val product: Product,
    val copies: Int,
    val atMillis: Long,
)

/**
 * Scan a barcode, print that product's label, repeat.
 *
 * The whole screen is one loop with no navigation in it: a scan resolves to exactly one product and
 * the label prints, so somebody labelling a delivery can work the trigger with one hand. The two
 * cases where "exactly one product" isn't true — nothing matched, or two products share the code —
 * stop the loop and say so rather than printing something plausible.
 */
class QuickPrintViewModel(
    private val repository: ProductRepository,
    private val labelRenderer: LabelRenderer,
    private val printer: PrinterDriver,
    private val labelSettingsRepository: LabelSettingsRepository,
) : ViewModel() {

    /** Whether this device has a built-in printer. */
    val hasPrinter: Boolean = printer.isAvailable

    val labelSettings: StateFlow<LabelSettings> = labelSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabelSettings())

    private val _state = MutableStateFlow<QuickPrintState>(QuickPrintState.Waiting)
    val state: StateFlow<QuickPrintState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<PrintedLabel>>(emptyList())
    val history: StateFlow<List<PrintedLabel>> = _history.asStateFlow()

    /**
     * Resolves a scanned or typed [code] and prints when it names exactly one published product.
     *
     * A scan arriving while the printer is still working is ignored rather than queued: the trigger
     * on these devices repeats easily, and a queue would turn one twitchy pull into a stack of
     * labels nobody asked for.
     */
    fun onCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        if (_state.value is QuickPrintState.Printing) return

        viewModelScope.launch {
            val matches = repository.findAllByCode(trimmed)
            val only = matches.singleOrNull()
            when {
                matches.isEmpty() -> _state.value = QuickPrintState.NoMatch(trimmed)
                only == null -> _state.value = QuickPrintState.Ambiguous(trimmed, matches)
                !only.isPublished -> _state.value = QuickPrintState.ConfirmUnpublished(only)
                else -> print(only)
            }
        }
    }

    /** Prints [product] now — the path taken by a clean scan, a confirmed draft, and a pick. */
    fun print(product: Product) {
        if (_state.value is QuickPrintState.Printing) return
        if (!printer.isAvailable) {
            _state.value = QuickPrintState.Failed("This device has no printer")
            return
        }
        val settings = labelSettings.value
        _state.value = QuickPrintState.Printing(product)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                printer.printBitmap(
                    bitmap = labelRenderer.render(product, settings),
                    copies = settings.copies,
                    feedLines = settings.spacing,
                    blackMark = settings.feedsToNextLabel,
                )
            }
            _state.value = when (result) {
                is PrintResult.Success -> {
                    remember(product, settings.copies)
                    QuickPrintState.Printed(product, settings.copies)
                }

                is PrintResult.Error -> QuickPrintState.Failed(result.message)
            }
        }
    }

    /** Back to waiting for the next trigger pull, without printing whatever is on screen. */
    fun dismiss() { _state.value = QuickPrintState.Waiting }

    fun setCopies(value: Int) {
        viewModelScope.launch { labelSettingsRepository.setCopies(value) }
    }

    fun clearHistory() { _history.value = emptyList() }

    /**
     * Adds to the running tally, newest first.
     *
     * Repeats are kept as separate entries rather than merged: the tally answers "how many have I
     * done?" while working through a pallet, and merging would hide the count it exists to show.
     */
    private fun remember(product: Product, copies: Int) {
        val entry = PrintedLabel(product, copies, System.currentTimeMillis())
        _history.value = (listOf(entry) + _history.value).take(MAX_HISTORY)
    }

    companion object {
        private const val MAX_HISTORY = 30

        fun factory(
            repository: ProductRepository,
            labelRenderer: LabelRenderer,
            printer: PrinterDriver,
            labelSettingsRepository: LabelSettingsRepository,
        ) = viewModelFactory {
            initializer {
                QuickPrintViewModel(repository, labelRenderer, printer, labelSettingsRepository)
            }
        }
    }
}
