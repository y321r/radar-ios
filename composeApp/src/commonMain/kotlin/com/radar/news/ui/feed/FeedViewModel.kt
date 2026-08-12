package com.radar.news.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radar.news.data.model.Article
import com.radar.news.data.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * KMP shell version of the Android FeedViewModel: same observable surface the UI needs,
 * with the platform bits (ads, WorkManager, DataStore, Hilt) stripped out for the port.
 * Notifications are tracked in memory only for now; wiring them per platform comes later.
 */
class FeedViewModel(
    private val repository: NewsRepository,
) : ViewModel() {

    /** The timeline as a plain list — paging is a later refinement on iOS. */
    val articles: Flow<List<Article>> = repository.observeLatest()

    /** Ad slots are not part of the KMP shell yet; the UI keys off this. */
    val loadedAdCount: StateFlow<Int> = MutableStateFlow(0)

    /**
     * Newest `publishedAt` the user has already seen at the top of the list. Anything newer
     * than this is what the pill offers to scroll to.
     */
    private val watermark = MutableStateFlow(Long.MAX_VALUE)

    /**
     * Stories that arrived above the user's current position.
     *
     * Starts at `Long.MAX_VALUE` so that nothing counts as "new" until the first watermark is
     * taken — otherwise every article in the database would be announced on first launch.
     */
    val newItemCount: StateFlow<Int> = watermark
        .flatMapLatest { mark ->
            if (mark == Long.MAX_VALUE) flowOf(0) else repository.observeCountNewerThan(mark)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        // First-run onboarding: the shell shows it once per process.
        _uiState.update { it.copy(showOnboarding = true) }
        refresh()
    }

    /**
     * Foreground sync, for app open and pull-to-refresh. Runs directly rather than through
     * WorkManager so it can drive the spinner and surface per-source failures. Does not
     * publish notifications (the background worker's job — later on each platform).
     */
    fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val outcome = runCatching { repository.sync(foreground = true) }.getOrNull()

            _uiState.update { state ->
                state.copy(
                    refreshing = false,
                    error = when {
                        outcome == null -> FeedError(allSourcesFailed = true)
                        // No network is not a source fault, so it is never attributed to one.
                        outcome.fetch.offline ->
                            FeedError(allSourcesFailed = true, offline = true)
                        outcome.fetch.allFailed -> FeedError(allSourcesFailed = true)
                        outcome.fetch.failures.isNotEmpty() -> FeedError(
                            allSourcesFailed = false,
                            failedSourceNames = outcome.fetch.failures.map { it.sourceName },
                        )
                        else -> null
                    },
                )
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    // ------------------------------------------------------------ new-posts pill ---

    /**
     * Marks everything currently stored as seen. Called whenever the user is at the top of the
     * list — either because they scrolled there or because they tapped the pill — which is
     * exactly when there is nothing left to defer.
     */
    fun markCurrentAsSeen() {
        viewModelScope.launch { watermark.value = repository.newestPublishedAt() }
    }

    // ------------------------------------------------------------- notifications ---

    /** Shell behaviour: flips the in-memory flag only. Platform permission wiring comes later. */
    fun setNotificationsEnabled(enabled: Boolean, systemPermissionGranted: Boolean = true) {
        val effective = enabled && systemPermissionGranted
        _uiState.update { it.copy(notificationsEnabled = effective) }
    }

    fun syncNotificationPermission(granted: Boolean) {
        if (!granted && _uiState.value.notificationsEnabled) {
            setNotificationsEnabled(enabled = false, systemPermissionGranted = false)
        }
    }

    // ---------------------------------------------------------------- onboarding ---

    /** Records that the prompt has been shown — it must never appear a second time. */
    fun onOnboardingAnswered(accepted: Boolean) {
        _uiState.update { it.copy(showOnboarding = false) }
        setNotificationsEnabled(accepted)
    }

    // ----------------------------------------------------------------- deep link ---

    fun onDeepLink(articleId: String?) =
        _uiState.update { it.copy(pendingDeepLinkArticleId = articleId) }

    fun onDeepLinkHandled() = _uiState.update { it.copy(pendingDeepLinkArticleId = null) }
}
