package com.radar.news

import com.radar.news.data.local.RadarDatabase
import com.radar.news.data.local.createRadarDatabase
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import radar_ios.composeapp.generated.resources.Res

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

        val client = HttpClient(Darwin) {
            install(HttpTimeout) {
                requestTimeoutMillis = 35_000
                connectTimeoutMillis = 15_000
            }
        }
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
