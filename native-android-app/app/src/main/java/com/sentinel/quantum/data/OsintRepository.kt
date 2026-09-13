package com.sentinel.quantum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OsintRepository {
    private val client = OkHttpClient()

    fun fetchRssFeed(url: String): List<String> = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) emptyList()
            else listOf(body.string())
        }
    } catch (_: Exception) { emptyList() }

    suspend fun fetchTextSafe(url: String): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext "Error: HTTP ${r.code}"
                r.body?.string() ?: "Empty body"
            }
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }

    fun fetchRawText(url: String): String = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) "Error" else body.string()
        }
    } catch (_: Exception) { "Error" }
}
