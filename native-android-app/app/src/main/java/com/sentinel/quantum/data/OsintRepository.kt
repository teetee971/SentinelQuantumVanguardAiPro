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
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchFeed(source: OsintSource): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        if (!source.url.startsWith("https://")) {
            return@withContext emptyList()
        }

        try {
            val request = Request.Builder()
                .url(source.url)
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

                feed.entries.map { entry ->
                    OsintFeedItem(
                        title = entry.title ?: "Sans titre",
                        description = entry.description?.value ?: "",
                        link = entry.link ?: "",
                        source = source.displayName,
                        pubDate = entry.publishedDate ?: java.util.Date(),
                        category = entry.categories.firstOrNull()?.name ?: ""
                    )
                }
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
