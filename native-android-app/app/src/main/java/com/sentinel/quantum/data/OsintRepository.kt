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
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    suspend fun fetchFeed(source: OsintSource): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(source.url)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }
            
            val xmlContent = response.body?.string() ?: return@withContext emptyList()
            
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
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun fetchAllFeeds(): List<OsintFeedItem> = withContext(Dispatchers.IO) {
        val allFeeds = mutableListOf<OsintFeedItem>()
        
        OsintSource.values().forEach { source ->
            try {
                val items = fetchFeed(source)
                allFeeds.addAll(items)
            } catch (e: Exception) {
                // Continue with other feeds if one fails
            }
        }
        
        // Sort by date, most recent first
        allFeeds.sortedByDescending { it.pubDate }
    }
}
