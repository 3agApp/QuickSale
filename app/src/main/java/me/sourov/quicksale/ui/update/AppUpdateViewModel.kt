package me.sourov.quicksale.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.sourov.quicksale.BuildConfig
import me.sourov.quicksale.data.update.AppRelease
import me.sourov.quicksale.data.update.AppUpdatePreferences
import me.sourov.quicksale.data.update.GitHubReleaseClient
import me.sourov.quicksale.data.update.isNewerVersion

data class AppUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val isCheckingLatest: Boolean = false,
    val isLoadingVersions: Boolean = false,
    val promptRelease: AppRelease? = null,
    val latestRelease: AppRelease? = null,
    val releases: List<AppRelease> = emptyList(),
    val skippedVersionTag: String? = null,
) {
    val latestAvailableUpdate: AppRelease?
        get() = latestRelease?.takeIf { isNewerVersion(it.versionName, currentVersionName) }
}

class AppUpdateViewModel(
    private val preferences: AppUpdatePreferences,
    private val releaseClient: GitHubReleaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var startupChecked = false

    init {
        viewModelScope.launch {
            preferences.skippedVersionTag.collect { tag ->
                _uiState.update { it.copy(skippedVersionTag = tag) }
            }
        }
    }

    fun checkOnAppStart() {
        if (startupChecked) return
        startupChecked = true
        checkLatest(showPrompt = true, showResultMessage = false)
    }

    fun checkLatest(showResultMessage: Boolean = true) {
        checkLatest(showPrompt = false, showResultMessage = showResultMessage)
    }

    fun loadVersions(showResultMessage: Boolean = false) {
        if (_uiState.value.isLoadingVersions) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVersions = true) }
            runCatching { releaseClient.fetchReleases() }
                .onSuccess { releases ->
                    val latestStable = releases.firstOrNull { !it.isPrerelease }
                    _uiState.update {
                        it.copy(
                            isLoadingVersions = false,
                            releases = releases,
                            latestRelease = latestStable,
                        )
                    }
                    if (showResultMessage) {
                        sendUpdateResultMessage(latestStable)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoadingVersions = false) }
                    _messages.send(error.message ?: "Couldn't load app versions")
                }
        }
    }

    fun dismissPrompt() {
        _uiState.update { it.copy(promptRelease = null) }
    }

    fun skipPromptVersion() {
        val release = _uiState.value.promptRelease ?: return
        viewModelScope.launch {
            preferences.skipVersion(release.tagName)
            _uiState.update { it.copy(promptRelease = null, skippedVersionTag = release.tagName) }
            _messages.send("Skipped ${release.versionName}")
        }
    }

    private fun checkLatest(showPrompt: Boolean, showResultMessage: Boolean) {
        if (_uiState.value.isCheckingLatest) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingLatest = true) }
            runCatching {
                val releases = releaseClient.fetchReleases()
                val skippedTag = preferences.skippedVersionTag.first()
                releases to skippedTag
            }.onSuccess { (releases, skippedTag) ->
                val latestStable = releases.firstOrNull { !it.isPrerelease }
                val update = latestStable?.takeIf {
                    isNewerVersion(it.versionName, BuildConfig.VERSION_NAME)
                }
                _uiState.update {
                    it.copy(
                        isCheckingLatest = false,
                        latestRelease = latestStable,
                        releases = releases,
                        skippedVersionTag = skippedTag,
                        promptRelease = if (
                            showPrompt &&
                            update != null &&
                            update.tagName != skippedTag
                        ) {
                            update
                        } else {
                            it.promptRelease
                        },
                    )
                }
                if (showResultMessage) {
                    sendUpdateResultMessage(latestStable)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isCheckingLatest = false) }
                if (showResultMessage) {
                    _messages.send(error.message ?: "Couldn't check for updates")
                }
            }
        }
    }

    private suspend fun sendUpdateResultMessage(latestStable: AppRelease?) {
        when {
            latestStable == null -> _messages.send("No GitHub releases found")
            isNewerVersion(latestStable.versionName, BuildConfig.VERSION_NAME) ->
                _messages.send("Update ${latestStable.versionName} is available")
            else -> _messages.send("You're on the latest version")
        }
    }

    companion object {
        fun factory(
            preferences: AppUpdatePreferences,
            releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
        ) = viewModelFactory {
            initializer { AppUpdateViewModel(preferences, releaseClient) }
        }
    }
}
