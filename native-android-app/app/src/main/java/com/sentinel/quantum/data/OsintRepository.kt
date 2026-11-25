package com.sentinel.quantum.data

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * @param cache optional local, bounded cache. When provided, a successful complete fetch is
 * persisted and can be read back via [loadCached] (e.g. when the app opens offline).
 */
class OsintRepository(private val cache: OsintFeedCache? = null) {

    private companion object {
        const val MAX_FEED_BYTES = 5 * 1024 * 1024
        const val MAX_FEED_ENTRIES = 500
        const val REQUEST_TIMEOUT_SECONDS = 20L
        const val UNKNOWN_PUBLISHED_AT_MS = 0L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private data class SourceFetch(val succeeded: Boolean, val items: List<OsintFeedItem>)

    data class FetchResult(
        val items: List<OsintFeedItem>,
        val successfulSourceCount: Int,
        val totalSourceCount: Int
    ) {
        val isComplete: Boolean get() = successfulSourceCount == totalSourceCount
    }

    suspend fun fetchFeed(source: OsintSource): List<OsintFeedItem> =
        fetchFeedResult(source).items

    private suspend fun fetchFeedResult(source: OsintSource): SourceFetch = withContext(Dispatchers.IO) {
        // Sources are a closed enum allowlist. Keep the URL scheme check as a second guard.
        if (!source.url.startsWith("https://")) {
            return@withContext SourceFetch(false, emptyList())
        }

        try {
            val nowMs = System.currentTimeMillis()
            val requestUrl = OsintPublicSources.requestUrl(source, nowMs)
                ?: return@withContext SourceFetch(false, emptyList())
            val accept = if (source.kind == OsintSourceKind.RSS) {
                "application/rss+xml, application/atom+xml, application/xml, text/xml"
            } else {
                "application/json"
            }
            val bytes = getBytes(requestUrl, accept) ?: return@withContext SourceFetch(false, emptyList())
            if (source.kind == OsintSourceKind.RSS) {
                return@withContext SourceFetch(true, parseRss(bytes, source))
            }
            if (source.kind == OsintSourceKind.CISA_KEV) {
                val items = OsintPublicSources.parseKev(bytes.toString(Charsets.UTF_8), source.displayName)
                    ?: return@withContext SourceFetch(false, emptyList())
                return@withContext SourceFetch(true, items)
            }
            val first = OsintPublicSources.parseNvd(bytes.toString(Charsets.UTF_8), source.displayName)
                ?: return@withContext SourceFetch(false, emptyList())
            val lastPage = OsintPublicSources.nvdLastPageStart(first.totalResults)
                ?: return@withContext SourceFetch(false, emptyList())
            if (lastPage == 0) return@withContext SourceFetch(true, first.items)
            val lastUrl = OsintPublicSources.requestUrl(source, nowMs, lastPage)
                ?: return@withContext SourceFetch(false, emptyList())
            val lastBytes = getBytes(lastUrl, accept) ?: return@withContext SourceFetch(false, emptyList())
            val last = OsintPublicSources.parseNvd(lastBytes.toString(Charsets.UTF_8), source.displayName)
                ?: return@withContext SourceFetch(false, emptyList())
            SourceFetch(true, last.items)
        } catch (_: Exception) {
            SourceFetch(false, emptyList())
        }
    }

    private fun getBytes(url: String, accept: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", "SentinelQuantumVanguardAiPro-OSINT/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body
            if (body.contentLength() > MAX_FEED_BYTES) return null
            return body.byteStream().use { input -> BoundedInputReader.read(input, MAX_FEED_BYTES) }
        }
    }

    private fun parseRss(xmlBytes: ByteArray, source: OsintSource): List<OsintFeedItem> {
        val feed: SyndFeed = ByteArrayInputStream(xmlBytes).use { input ->
            SyndFeedInput().build(XmlReader(input))
        }
        return feed.entries
            .asSequence()
            .take(MAX_FEED_ENTRIES)
            .map { entry ->
                OsintFeedItem(
                    title = entry.title.orEmpty(),
                    description = entry.description?.value ?: "",
                    link = entry.link ?: "",
                    source = source.displayName,
                    pubDate = entry.publishedDate ?: Date(UNKNOWN_PUBLISHED_AT_MS),
                    category = entry.categories.firstOrNull()?.name ?: ""
                )
            }
            .toList()
    }

    suspend fun fetchAllFeedsResult(): FetchResult = withContext(Dispatchers.IO) {
        val results = OsintSource.entries.map { fetchFeedResult(it) }
        val items = results.flatMap { it.items }.sortedByDescending { it.pubDate }
        FetchResult(items, results.count { it.succeeded }, results.size)
    }

    /**
     * Compatibility path for callers that only consume a list. A partial refresh is never
     * returned as if it were a complete fresh snapshot. Callers needing partial visibility must
     * use [fetchAllFeedsResult] and surface source coverage explicitly.
     */
    suspend fun fetchAllFeeds(): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        val result = fetchAllFeedsResult()
        if (!result.isComplete || result.items.isEmpty()) {
            return@withContext emptyList()
        }
        cache?.save(result.items)
        result.items
    }

    /** Returns the last locally cached snapshot, if any. Performs no network access. */
    fun loadCached(): OsintFeedCache.CachedFeed? = cache?.load()
}
