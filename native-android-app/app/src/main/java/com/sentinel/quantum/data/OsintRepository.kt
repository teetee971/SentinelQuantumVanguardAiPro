package com.sentinel.quantum.data
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import okhttp3.OkHttpClient
import okhttp3.Request
class OsintRepository {
    private val client = OkHttpClient()
    fun fetchRssFeed(url: String): List<String> = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) emptyList()
            else SyndFeedInput().build(XmlReader(body.byteStream())).entries.take(10).map { it.title ?: "No Title" }
        }
    } catch (_: Exception) { emptyList() }
    fun fetchRawText(url: String): String = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) "Error" else body.string()
        }
    } catch (_: Exception) { "Error" }
}
