package me.sourov.quicksale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.sourov.quicksale.data.settings.ConnectionResult
import me.sourov.quicksale.data.settings.ConnectionTester
import me.sourov.quicksale.data.settings.HTTPS_SITE_URL_PREFIX
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.WooKeyParser
import me.sourov.quicksale.data.settings.hasSiteUrlHost
import me.sourov.quicksale.data.settings.namesSiteScheme
import me.sourov.quicksale.data.settings.normalizeSiteUrl
import me.sourov.quicksale.data.settings.toSiteHostInput
import me.sourov.quicksale.data.settings.toSiteUrlParts
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
    /** Just the host — the scheme is a fixed, non-editable prefix with its own control. */
    val siteHost: String = "",
    /** `https://` or `http://`, kept out of [siteHost] so it can't be half-deleted. */
    val siteScheme: String = HTTPS_SITE_URL_PREFIX,
    val consumerKey: String = "",
    val consumerSecret: String = "",
    /** Whether to talk to this store without checking its TLS certificate. */
    val allowInsecureTls: Boolean = true,
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

    /** The `scheme://host` this screen would save, ready for [normalizeSiteUrl]. */
    val siteUrlInput: String get() = siteScheme + siteHost

    val isDirty: Boolean
        get() = siteHost != saved.siteUrl.toSiteHostInput() ||
            siteScheme != saved.siteUrl.toSiteUrlParts().scheme ||
            consumerKey != saved.consumerKey ||
            consumerSecret != saved.consumerSecret ||
            allowInsecureTls != saved.allowInsecureTls

    val canSave: Boolean
        get() = !isSaving && isDirty && hasSiteUrlHost(siteHost) && hasCredentials

    val canTest: Boolean
        get() = connectionTest != ConnectionTestState.Testing &&
            hasSiteUrlHost(siteHost) && hasCredentials
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
                            siteScheme = stored.siteUrl.toSiteUrlParts().scheme,
                            allowInsecureTls = stored.allowInsecureTls,
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
        it.copy(
            siteHost = value.toSiteHostInput(),
            // A pasted `http://…` moves the switch for the operator; typing a bare host leaves it
            // wherever they put it, since every keystroke would otherwise snap it back to https.
            siteScheme = if (value.namesSiteScheme()) value.toSiteUrlParts().scheme else it.siteScheme,
            connectionTest = ConnectionTestState.Idle,
        )
    }

    fun onSiteSchemeChange(scheme: String) = _uiState.update {
        it.copy(siteScheme = scheme, connectionTest = ConnectionTestState.Idle)
    }

    /** Clears any previous test result: the last one was reached under the other trust setting. */
    fun onAllowInsecureTlsChange(value: Boolean) = _uiState.update {
        it.copy(allowInsecureTls = value, connectionTest = ConnectionTestState.Idle)
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
            val normalizedSiteUrl = normalizeSiteUrl(state.siteUrlInput)
                ?: return@launch _uiState.update {
                    it.copy(connectionTest = ConnectionTestState.Failure("Enter a valid store URL"))
                }
            val result = connectionTester.test(
                StoreSettings(
                    siteUrl = normalizedSiteUrl,
                    consumerKey = state.consumerKey,
                    consumerSecret = state.consumerSecret,
                    allowInsecureTls = state.allowInsecureTls,
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
            val normalizedSiteUrl = normalizeSiteUrl(state.siteUrlInput)
                ?: return@launch _messages.send("Enter a valid store URL")
            _uiState.update { it.copy(isSaving = true) }
            repository.update(
                StoreSettings(
                    siteUrl = normalizedSiteUrl,
                    consumerKey = state.consumerKey,
                    consumerSecret = state.consumerSecret,
                    allowInsecureTls = state.allowInsecureTls,
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
