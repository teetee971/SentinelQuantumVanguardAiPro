package com.sentinel.quantum.data

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.util.concurrent.TimeUnit

class OsintRepository {

    private companion object {
        const val MAX_FEED_BYTES = 5L * 1024L * 1024L
        const val MAX_FEED_ENTRIES = 500
        const val REQUEST_TIMEOUT_SECONDS = 20L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun fetchFeed(source: OsintSource): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        // Sources are a closed enum allowlist. Keep the URL scheme check as a second guard.
        if (!source.url.startsWith("https://")) {
            return@withContext emptyList()
        }

        try {
            val request = Request.Builder()
                .url(source.url)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "SentinelQuantumVanguardAiPro-OSINT/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val body = response.body ?: return@withContext emptyList()
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_FEED_BYTES) {
                    return@withContext emptyList()
                }

                val xmlContent = body.string()
                if (xmlContent.toByteArray(Charsets.UTF_8).size > MAX_FEED_BYTES) {
                    return@withContext emptyList()
                }

                val feed: SyndFeed = SyndFeedInput().build(XmlReader(StringReader(xmlContent)))

                feed.entries
                    .asSequence()
                    .take(MAX_FEED_ENTRIES)
                    .map { entry ->
                        OsintFeedItem(
                            title = entry.title ?: "Sans titre",
                            description = entry.description?.value ?: "",
                            link = entry.link ?: "",
                            source = source.displayName,
                            pubDate = entry.publishedDate ?: java.util.Date(),
                            category = entry.categories.firstOrNull()?.name ?: ""
                        )
                    }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAllFeeds(): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        val allFeeds = mutableListOf<OsintFeedItem>()

        OsintSource.values().forEach { source ->
            allFeeds.addAll(fetchFeed(source))
        }

        allFeeds.sortedByDescending { it.pubDate }
    }
}
