package com.sentinel.quantum.security

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only client for the same RTR allocation index used by the Sentinel web directory. */
class RtrDirectoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).build()
) {
    @Volatile private var cachedDirectory: JSONObject? = null

    data class Allocation(
        val start: String, val end: String, val category: String, val area: String?,
        val status: String, val allocationHolder: String?, val holderId: String?
    )
    data class Result(val status: String, val matches: List<Allocation>)

    fun lookup(raw: String): Result? {
        val number = normalize(raw) ?: return null
        return find(loadDirectory(), number)
    }

    @Synchronized
    private fun loadDirectory(): JSONObject {
        cachedDirectory?.let { return it }
        val req = Request.Builder().url(DIRECTORY_URL).header("Accept", "application/json").get().build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("RTR_HTTP_${response.code}")
            val body = response.body
            if (body.contentLength() == 0L || body.contentLength() > MAX_BYTES) throw IllegalStateException("RTR_SIZE")
            val bytes = body.bytes()
            if (bytes.size > MAX_BYTES) throw IllegalStateException("RTR_SIZE")
            val parsed = JSONObject(String(bytes, Charsets.UTF_8))
            // Validate before caching; incompatible data must fail closed and remain uncached.
            if (parsed.optInt("schemaVersion") != 1 || parsed.optString("country") != "AT" ||
                parsed.optJSONArray("holders") == null || parsed.optJSONObject("groups") == null) {
                throw IllegalStateException("RTR_SCHEMA")
            }
            cachedDirectory = parsed
            return parsed
        }
    }

    companion object {
        const val DIRECTORY_URL = "https://sentinelquantumvanguardaipro.pages.dev/public/data/rtr-numbering.json"
        private const val MAX_BYTES = 4 * 1024 * 1024
        private val statuses = listOf("unallocated", "outside-allocation", "not-issued", "partial", "not-allocatable")

        internal fun normalize(raw: String): String? {
            val compact = raw.trim().replace(Regex("[\\s().-]"), "")
            return when {
                compact.matches(Regex("^\\+43[1-9]\\d{4,12}$")) -> compact
                compact.matches(Regex("^0043[1-9]\\d{4,12}$")) -> "+" + compact.substring(2)
                else -> null
            }
        }

        internal fun find(directory: JSONObject, number: String): Result {
            if (directory.optInt("schemaVersion") != 1 || directory.optString("country") != "AT") throw IllegalStateException("RTR_SCHEMA")
            val holders = directory.optJSONArray("holders") ?: throw IllegalStateException("RTR_HOLDERS")
            val groups = directory.optJSONObject("groups") ?: throw IllegalStateException("RTR_GROUPS")
            val national = number.substring(3)
            val matches = mutableListOf<Allocation>()
            for (prefixLength in 1..4) {
                if (national.length <= prefixLength) continue
                val prefix = national.substring(0, prefixLength)
                val suffix = national.substring(prefixLength)
                val group = groups.optJSONObject("$prefix/${suffix.length}") ?: continue
                val ranges = group.optJSONArray("ranges") ?: continue
                for (i in 0 until ranges.length()) {
                    val range = ranges.optJSONArray(i) ?: continue
                    if (range.length() != 3) continue
                    val start = range.optString(0); val end = range.optString(1)
                    if (suffix < start || suffix > end) continue
                    val holderIndex = range.optInt(2, Int.MIN_VALUE)
                    val holder = if (holderIndex >= 0 && holderIndex < holders.length()) holders.optJSONArray(holderIndex) else null
                    val status = if (holder != null) "allocated" else statuses.getOrNull(-holderIndex - 1) ?: "unknown"
                    matches += Allocation(
                        "+43$prefix$start", "+43$prefix$end",
                        group.optString("category"), group.optString("area").takeIf { it.isNotBlank() },
                        status, holder?.optString(0)?.takeIf { it.isNotBlank() },
                        holder?.optString(1)?.takeIf { it.isNotBlank() }
                    )
                }
            }
            return Result(if (matches.size > 1) "ambiguous" else matches.firstOrNull()?.status ?: "no-match", matches)
        }
    }
}
