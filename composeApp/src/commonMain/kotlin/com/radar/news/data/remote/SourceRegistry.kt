package com.radar.news.data.remote

import com.radar.news.data.model.AdapterType
import com.radar.news.data.model.NewsSource
import com.radar.news.data.model.SourceRegistryFile
import kotlinx.serialization.json.Json

/**
 * Holds the registry of news sources and hands out the right adapter for each.
 *
 * KMP note: the Android original loaded `sources.json` from `assets` via `Context`. Here the
 * raw JSON text is injected — the platform layer (Android `assets`, iOS bundle resource)
 * loads it once at wiring time, so this class stays pure Kotlin.
 */
class SourceRegistry(
    private val sourcesJson: String,
    private val rssAdapter: RssAdapter,
    private val atomAdapter: AtomAdapter,
    private val googleNewsAdapter: GoogleNewsAdapter,
    private val htmlAdapter: HtmlAdapter,
) {
    private val json = Json {
        ignoreUnknownKeys = true // `_comment`, `notes` and friends are documentation, not data
        isLenient = true
    }

    private var cached: List<NewsSource>? = null

    fun all(): List<NewsSource> = cached ?: run {
        val loaded = runCatching {
            json.decodeFromString<SourceRegistryFile>(sourcesJson).sources
        }.getOrElse { emptyList() }
        cached = loaded
        loaded
    }

    fun enabled(): List<NewsSource> = all().filter { it.enabled }

    fun byId(id: String): NewsSource? = all().firstOrNull { it.id == id }

    fun adapterFor(source: NewsSource): FeedAdapter = when (source.adapter) {
        AdapterType.RSS -> rssAdapter
        AdapterType.ATOM -> atomAdapter
        AdapterType.GOOGLENEWS -> googleNewsAdapter
        AdapterType.HTML -> htmlAdapter
    }
}
