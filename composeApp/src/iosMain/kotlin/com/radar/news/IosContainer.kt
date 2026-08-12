package com.radar.news

import com.radar.news.data.local.RadarDatabase
import com.radar.news.data.remote.AtomAdapter
import com.radar.news.data.remote.FeedFetcher
import com.radar.news.data.remote.GoogleNewsAdapter
import com.radar.news.data.remote.HtmlAdapter
import com.radar.news.data.remote.RssAdapter
import com.radar.news.data.remote.SourceRegistry
import com.radar.news.data.repository.NewsRepository
import com.radar.news.domain.dedupe.Deduplicator
import com.radar.news.domain.filter.BreakingNewsClassifier
import com.radar.news.domain.filter.KeywordStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jetbrains.compose.resources.Res
import kotlin.time.Duration.Companion.seconds

/**
 * iOS dependency container — the counterpart of the Android `AppContainer`. Assets ship in
 * the Compose resources (`composeResources/files/…`) so the same JSON files serve every
 * platform; Room runs on the bundled SQLite driver.
 */
object IosContainer {
    val instance: NewsContainer by lazy { build() }

    private fun build(): NewsContainer = runBlocking {
        val sourcesJson = Res.readBytes("files/sources.json").decodeToString()
        val keywordsJson = Res.readBytes("files/keywords.json").decodeToString()

        val client = OkHttpClient.Builder()
            .callTimeout(35.seconds)
            .build()
        val fetcher = FeedFetcher(client)
        val database = createRadarDatabase()

        NewsContainer(
            repository = NewsRepository(
                registry = SourceRegistry(
                    sourcesJson = sourcesJson,
                    rssAdapter = RssAdapter(fetcher),
                    atomAdapter = AtomAdapter(fetcher),
                    googleNewsAdapter = GoogleNewsAdapter(fetcher, client),
                    htmlAdapter = HtmlAdapter(fetcher),
                ),
                dao = database.articleDao(),
                classifier = BreakingNewsClassifier(KeywordStore(keywordsJson)),
                deduplicator = Deduplicator(),
            ),
        )
    }
}

class NewsContainer(val repository: NewsRepository)
