package me.sourov.quicksale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.sourov.quicksale.data.settings.ConnectionResult
import me.sourov.quicksale.data.settings.ConnectionTester
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.WooKeyParser
import me.sourov.quicksale.data.settings.hasHttpsSiteUrlHost
import me.sourov.quicksale.data.settings.normalizeHttpsSiteUrl
import me.sourov.quicksale.data.settings.toSiteHostInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState

    /** WooCommerce answered but the organization routes didn't — keys fine, plugin not. */
    data class Partial(val message: String) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

data class SettingsUiState(
    /** Just the host — the field renders "https://" as a fixed, non-editable prefix. */
    val siteHost: String = "",
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Persisted values, used for connection status and unsaved-change detection. */
    val saved: StoreSettings = StoreSettings(),
    /** Whether the key/secret inputs are shown (after choosing manual entry or a scan). */
    val showCredentialFields: Boolean = false,
    val connectionTest: ConnectionTestState = ConnectionTestState.Idle,
) {
    private val hasCredentials: Boolean
        get() = consumerKey.isNotBlank() && consumerSecret.isNotBlank()

    val isDirty: Boolean
        get() = siteHost != saved.siteUrl.toSiteHostInput() ||
            consumerKey != saved.consumerKey ||
            consumerSecret != saved.consumerSecret

    val canSave: Boolean
        get() = !isSaving && isDirty && hasHttpsSiteUrlHost(siteHost) && hasCredentials

    val canTest: Boolean
        get() = connectionTest != ConnectionTestState.Testing &&
            hasHttpsSiteUrlHost(siteHost) && hasCredentials
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val connectionTester: ConnectionTester,
    /** Run after credentials change, to discard stale caches and pull the new store. */
    private val onStoreChanged: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { stored ->
                _uiState.update { state ->
                    if (state.isLoading) {
                        state.copy(
                            siteHost = stored.siteUrl.toSiteHostInput(),
                            consumerKey = stored.consumerKey,
                            consumerSecret = stored.consumerSecret,
                            saved = stored,
                            isLoading = false,
                            showCredentialFields = stored.consumerKey.isNotBlank() ||
                                stored.consumerSecret.isNotBlank(),
                        )
                    } else {
                        state.copy(saved = stored)
                    }
                }
            }
        }
    }

    fun onSiteUrlChange(value: String) = _uiState.update {
        it.copy(siteHost = value.toSiteHostInput(), connectionTest = ConnectionTestState.Idle)
    }

    fun onConsumerKeyChange(value: String) = _uiState.update {
        it.copy(consumerKey = value, connectionTest = ConnectionTestState.Idle)
    }

    fun onConsumerSecretChange(value: String) = _uiState.update {
        it.copy(consumerSecret = value, connectionTest = ConnectionTestState.Idle)
    }

    /** User chose to type keys instead of scanning. */
    fun enterManualEntry() = _uiState.update { it.copy(showCredentialFields = true) }

    /** Handles raw text captured from a scanned WooCommerce key QR code. */
    fun onCredentialsScanned(raw: String) {
        val parsed = WooKeyParser.parse(raw)
        if (parsed == null) {
            viewModelScope.launch { _messages.send("Couldn't read API keys from that code") }
            return
        }
        _uiState.update {
            it.copy(
                consumerKey = parsed.consumerKey,
                consumerSecret = parsed.consumerSecret,
                showCredentialFields = true,
                connectionTest = ConnectionTestState.Idle,
            )
        }
        viewModelScope.launch { _messages.send("API keys scanned") }
    }

    fun testConnection() {
        val state = _uiState.value
        if (!state.canTest) return
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTest = ConnectionTestState.Testing) }
            val normalizedSiteUrl = normalizeHttpsSiteUrl(state.siteHost)
                ?: return@launch _uiState.update {
                    it.copy(connectionTest = ConnectionTestState.Failure("Enter a valid store URL"))
                }
            val result = connectionTester.test(
                StoreSettings(
                    siteUrl = normalizedSiteUrl,
                    consumerKey = state.consumerKey,
                    consumerSecret = state.consumerSecret,
                )
            )
            _uiState.update {
                it.copy(
                    connectionTest = when (result) {
                        is ConnectionResult.Success -> ConnectionTestState.Success(result.message)
                        is ConnectionResult.Partial -> ConnectionTestState.Partial(result.message)
                        is ConnectionResult.Failure -> ConnectionTestState.Failure(result.message)
                    }
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val normalizedSiteUrl = normalizeHttpsSiteUrl(state.siteHost)
                ?: return@launch _messages.send("Enter a valid store URL")
            _uiState.update { it.copy(isSaving = true) }
            repository.update(
                StoreSettings(
                    siteUrl = normalizedSiteUrl,
                    consumerKey = state.consumerKey,
                    consumerSecret = state.consumerSecret,
                )
            )
            _uiState.update { it.copy(isSaving = false) }
            // New credentials can mean a different store entirely, so any cached ETags and the
            // data they describe are stale by definition — hand off and pull everything fresh.
            onStoreChanged()
            _messages.send("Store settings saved — syncing")
        }
    }

    companion object {
        fun factory(
            repository: SettingsRepository,
            connectionTester: ConnectionTester = ConnectionTester(),
            onStoreChanged: suspend () -> Unit = {},
        ) = viewModelFactory {
            initializer { SettingsViewModel(repository, connectionTester, onStoreChanged) }
        }
    }
}
