package com.radar.news

import android.app.Application
import android.content.Context
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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * Manual dependency container — the KMP shell's replacement for Hilt. Everything the feed
 * needs is built once here; per-platform assets (sources.json, keywords.json) are loaded by
 * the platform shell and passed in as text.
 */
class AppContainer(context: Context) {

    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 35_000
            connectTimeoutMillis = 15_000
        }
    }

    private val fetcher = FeedFetcher(client)

    private val database = createRadarDatabase(context)
    private val dao = database.articleDao()

    private val registry = SourceRegistry(
        sourcesJson = loadAsset(context, "sources.json"),
        rssAdapter = RssAdapter(fetcher),
        atomAdapter = AtomAdapter(fetcher),
        googleNewsAdapter = GoogleNewsAdapter(fetcher, client),
        htmlAdapter = HtmlAdapter(fetcher),
    )

    private val classifier = BreakingNewsClassifier(
        KeywordStore(loadAsset(context, "keywords.json")),
    )

    val repository: NewsRepository = NewsRepository(
        registry = registry,
        dao = dao,
        classifier = classifier,
        deduplicator = Deduplicator(),
    )

    private fun loadAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

class RadarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.radar.news.ui.AndroidAppContext.context = this
    }
}
