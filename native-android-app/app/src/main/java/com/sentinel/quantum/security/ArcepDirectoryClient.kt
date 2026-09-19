package com.sentinel.quantum.security

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Read-only Android client for the same generated ARCEP allocation index used by the public site.
 * Allocation data identifies the published block holder; it is not proof of the current carrier
 * after portability, the caller's identity, or fraud.
 */
class ArcepDirectoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()
) {
    data class Allocation(
        val start: String, val end: String, val operatorCode: String,
        val attributedOperator: String?, val territory: String?, val allocationDate: String?,
        val businessIdentifier: String?, val rcs: String?, val address: String?,
        val canReceiveNumbering: Boolean?, val declarationDate: String?
    )

    fun lookup(raw: String): Allocation? {
        val national = toFrenchNational(raw) ?: return null
        val request = Request.Builder().url(DIRECTORY_URL).header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("ARCEP_HTTP_${response.code}")
            val body = response.body
            if ((body.contentLength() > MAX_BYTES) || body.contentLength() == 0L) throw IllegalStateException("ARCEP_SIZE")
            val bytes = body.bytes()
            if (bytes.size > MAX_BYTES) throw IllegalStateException("ARCEP_SIZE")
            return find(JSONObject(String(bytes, Charsets.UTF_8)), national)
        }
    }

    companion object {
        const val DIRECTORY_URL = "https://sentinelquantumvanguardaipro.pages.dev/public/data/arcep-numbering.json"
        private const val MAX_BYTES = 8 * 1024 * 1024

        internal fun toFrenchNational(raw: String): String? {
            val compact = raw.trim().replace(Regex("[\\s().-]"), "")
            val national = when {
                compact.matches(Regex("^\\+33[1-9]\\d{8}$")) -> "0" + compact.substring(3)
                compact.matches(Regex("^0033[1-9]\\d{8}$")) -> "0" + compact.substring(4)
                compact.matches(Regex("^0[1-9]\\d{8}$")) -> compact
                compact.matches(Regex("^\\d{4,6}$")) -> compact
                else -> return null
            }
            return national
        }

        internal fun find(directory: JSONObject, national: String): Allocation? {
            if (directory.optInt("schemaVersion") != 2) throw IllegalStateException("ARCEP_SCHEMA")
            val entries = directory.optJSONArray("entries") ?: throw IllegalStateException("ARCEP_ENTRIES")
            if (entries.length() > 150_000) throw IllegalStateException("ARCEP_ENTRIES")
            for (i in 0 until entries.length()) {
                val e = entries.optJSONArray(i) ?: continue
                if (e.length() < 5) continue
                val start = e.optString(0); val end = e.optString(1)
                if (start.length != national.length || end.length != national.length || national < start || national > end) continue
                val code = e.optString(2)
                val op = directory.optJSONObject("operators")?.optJSONArray(code)
                return Allocation(start, end, code,
                    op?.optString(0)?.takeIf { it.isNotBlank() },
                    e.optString(3).takeIf { it.isNotBlank() }, e.optString(4).takeIf { it.isNotBlank() },
                    op?.optString(1)?.takeIf { it.isNotBlank() }, op?.optString(2)?.takeIf { it.isNotBlank() },
                    op?.optString(3)?.takeIf { it.isNotBlank() },
                    if (op != null && op.length() > 4 && !op.isNull(4)) op.optBoolean(4) else null,
                    op?.optString(5)?.takeIf { it.isNotBlank() })
            }
            return null
        }
    }
}
