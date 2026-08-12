package com.radar.news.ui.feed

/**
 * Screen state that sits *alongside* the paged list rather than containing it.
 *
 * Paging owns loading and empty for the list itself; this carries what Paging cannot know —
 * whether a network sync is in flight, and whether the last one failed.
 */
data class FeedUiState(
    val refreshing: Boolean = false,
    /**
     * Set when a sync failed. Rendered as a full error state only when there is nothing
     * cached to show; otherwise it becomes a dismissible strip over the existing timeline,
     * because stale news beats an error page.
     */
    val error: FeedError? = null,
    val notificationsEnabled: Boolean = false,
    /** True on the very first launch, before the user has answered the permission prompt. */
    val showOnboarding: Boolean = false,
    /** Article id a notification deep link asked us to scroll to. */
    val pendingDeepLinkArticleId: String? = null,
)

/** Why a sync failed, in the detail the UI actually distinguishes. */
data class FeedError(
    val allSourcesFailed: Boolean,
    val failedSourceNames: List<String> = emptyList(),
    /**
     * The device had no network, rather than the outlets being at fault.
     *
     * Kept separate so the dismissible strip stays quiet for it: naming six healthy outlets as
     * failed because the phone was offline is simply wrong, and an outage is usually momentary.
     * The full-screen error state still applies when there is nothing cached to show, because
     * then the user does need telling why the timeline is empty.
     */
    val offline: Boolean = false,
)
