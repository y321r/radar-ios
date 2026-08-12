package com.radar.news

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radar.news.ui.feed.FeedScreen
import com.radar.news.ui.feed.FeedViewModel
import com.radar.news.ui.theme.RadarTheme

/** iOS root composable — same FeedScreen as Android, backed by the iOS container. */
@Composable
fun App() {
    RadarTheme {
        val container = IosContainer.instance
        val viewModel: FeedViewModel = viewModel { FeedViewModel(container.repository) }
        FeedScreen(viewModel = viewModel)
    }
}
