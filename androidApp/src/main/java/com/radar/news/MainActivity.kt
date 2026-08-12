package com.radar.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radar.news.ui.feed.FeedScreen
import com.radar.news.ui.feed.FeedViewModel
import com.radar.news.ui.theme.RadarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RadarApp).container
        setContent {
            RadarTheme {
                val viewModel: FeedViewModel = viewModel {
                    FeedViewModel(container.repository)
                }
                FeedScreen(
                    viewModel = viewModel,
                    onRequestNotificationPermission = { /* POST_NOTIFICATIONS wiring later */ },
                )
            }
        }
    }
}
