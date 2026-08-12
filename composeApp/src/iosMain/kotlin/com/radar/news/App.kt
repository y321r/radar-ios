package com.radar.news

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.radar.news.ui.feed.FeedScreen
import com.radar.news.ui.feed.FeedViewModel
import com.radar.news.ui.theme.RadarTheme

/** iOS root composable — same FeedScreen as Android, backed by the iOS container. */
@Composable
fun App() {
    RadarTheme {
        val viewModel = remember { FeedViewModel(IosContainer.instance.repository) }
        FeedScreen(viewModel = viewModel)
    }
}
