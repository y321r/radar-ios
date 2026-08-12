package com.radar.news.ui.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.radar.news.data.model.Article
import com.radar.news.ui.components.ArticleItem
import com.radar.news.ui.components.ContactSheet
import com.radar.news.ui.components.NewPostsPill
import com.radar.news.ui.components.NotificationPermissionDialog
import com.radar.news.ui.components.RadarTopBar
import com.radar.news.ui.components.ShimmerRow
import com.radar.news.ui.components.rememberNowTicker
import com.radar.news.ui.theme.Dimens
import com.radar.news.ui.theme.RadarColors
import kotlinx.coroutines.launch

/**
 * The whole app: a pinned top bar over one pull-to-refresh timeline.
 *
 * KMP shell version: the Android original interleaved AdMob slots via [FeedSlot] and used
 * Paging; both are stripped here. The timeline is a plain LazyColumn over the repository's
 * list flow — ads and paging are later refinements on each platform.
 *
 * State precedence is deliberate — a cached timeline always beats an error page. A failed
 * sync only takes over the screen when there is nothing at all to show; otherwise it is
 * demoted to a dismissible strip above the list.
 */
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val articles by viewModel.articles.collectAsState(initial = emptyList())
    val newItemCount by viewModel.newItemCount.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The contact sheet (News policy requirement) is closed until the top-bar button opens it.
    var showContact by remember { mutableStateOf(false) }

    // One shared clock for every visible timestamp — see rememberNowTicker.
    val now by rememberNowTicker()

    // Deep link from a breaking-news notification: scroll to the story it announced.
    LaunchedEffect(uiState.pendingDeepLinkArticleId, articles.size) {
        val targetId = uiState.pendingDeepLinkArticleId ?: return@LaunchedEffect
        val index = articles.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.onDeepLinkHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.Background),
    ) {
        // Edge-to-edge: the bar paints behind the status bar, so its content is inset down
        // to sit below the clock rather than under it.
        RadarTopBar(
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            notificationsEnabled = uiState.notificationsEnabled,
            onToggleNotifications = {
                if (uiState.notificationsEnabled) {
                    viewModel.setNotificationsEnabled(false)
                } else {
                    onRequestNotificationPermission()
                }
            },
            onContactClick = { showContact = true },
        )

        val isEmpty = articles.isEmpty()

        // An outage is not shown as a strip: the outlets are fine, and the cached timeline below
        // is still worth reading. When there is nothing cached, the full error state still runs.
        if (uiState.error?.offline == false && !isEmpty) {
            FeedErrorStrip(
                onRetry = viewModel::refresh,
                onDismiss = viewModel::dismissError,
            )
        }

        // At the top there is nothing to defer, so new stories just fade in; the watermark is
        // taken continuously while the user sits there.
        val atTop by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
        LaunchedEffect(atTop, articles.size) {
            if (atTop) viewModel.markCurrentAsSeen()
        }

        Box(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // Nothing cached and a sync just failed outright.
                    isEmpty && uiState.refreshing == false && uiState.error?.allSourcesFailed == true ->
                        FeedErrorState(onRetry = viewModel::refresh)

                    // First run: skeleton rows matching the real layout.
                    isEmpty && (uiState.refreshing || uiState.error == null) ->
                        FeedLoadingState()

                    // Synced fine, but nothing cleared the breaking + topic thresholds.
                    isEmpty -> FeedEmptyState()

                    else -> ArticleList(
                        articles = articles,
                        now = now,
                        listState = listState,
                    )
                }
            }

            // Floats over the list, under the top bar. Deferring the jump is the whole point:
            // a 15-minute background sync must not move content under a reading thumb.
            NewPostsPill(
                visible = !atTop,
                count = newItemCount,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                        viewModel.markCurrentAsSeen()
                    }
                },
            )
        }
    }

    if (uiState.showOnboarding) {
        NotificationPermissionDialog(
            onAccept = {
                viewModel.onOnboardingAnswered(accepted = true)
                onRequestNotificationPermission()
            },
            onLater = { viewModel.onOnboardingAnswered(accepted = false) },
        )
    }

    if (showContact) {
        ContactSheet(onDismiss = { showContact = false })
    }
}

@Composable
private fun ArticleList(
    articles: List<Article>,
    now: Long,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(RadarColors.Background),
        // The list scrolls behind the navigation bar; padding the content means the last row
        // can still be scrolled clear of it.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(
            items = articles,
            key = { it.id },
        ) { article ->
            // Subtle by design: a fade plus a placement shift, no bounce and no scale.
            // A newly synced story should appear, not announce itself.
            Column(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(ITEM_ANIMATION_MILLIS),
                    placementSpec = tween(ITEM_ANIMATION_MILLIS),
                    fadeOutSpec = tween(ITEM_ANIMATION_MILLIS),
                ),
            ) {
                ArticleItem(article = article, now = now)
                HorizontalDivider(
                    thickness = Dimens.DividerThickness,
                    color = RadarColors.Divider,
                )
            }
        }
    }
}

/** Fast enough not to delay reading, slow enough to show where the row came from. */
private const val ITEM_ANIMATION_MILLIS = 200
