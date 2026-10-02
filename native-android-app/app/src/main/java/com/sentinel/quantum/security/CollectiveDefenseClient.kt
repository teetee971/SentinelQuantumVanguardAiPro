package com.sentinel.quantum.security

import com.sentinel.quantum.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Public, credential-free Collective Defense client.
 *
 * Raw indicators are transmitted only for an explicit user-requested lookup/report.
 * Watch rechecks use only the server-issued HMAC fingerprint and never require the
 * original URL, domain, email address or SHA-256 value to be persisted.
 */
class CollectiveDefenseClient(
    private val baseUrl: String = BuildConfig.WANGIRI_API_BASE_URL,
    private val allowedHosts: Set<String> = setOf(PRODUCTION_HOST),
    private val client: OkHttpClient = defaultClient()
) {
    enum class IndicatorType { DOMAIN, URL, EMAIL, SHA256 }

    enum class ReportCategory {
        PHISHING,
        MALWARE,
        CREDENTIAL_THEFT,
        BANK_IMPERSONATION,
        DELIVERY_SCAM,
        TECH_SUPPORT_SCAM,
        ACCOUNT_TAKEOVER,
        INVESTMENT_SCAM,
        ROMANCE_SCAM,
        OTHER
    }

    data class ReputationResult(
        val indicatorType: IndicatorType,
        val indicatorFingerprint: String?,
        val riskState: String,
        val signals: Int,
        val categories: List<String>,
        val communityIntelligence: String,
        val reputationObservedAtMs: Long?,
        val reputationTtlMs: Long?,
        val enforcementAllowed: Boolean,
        val warning: String
    )

    data class ReportResult(
        val status: String,
        val indicatorFingerprint: String?,
        val effectOnReputation: String
    )

    fun lookup(type: IndicatorType, value: String): ReputationResult {
        require(value.isNotBlank()) { "COLLECTIVE_VALUE_REQUIRED" }
        val body = JSONObject()
            .put("indicator_type", type.name)
            .put("value", value.take(MAX_RAW_VALUE_CHARS))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        return executeReputation("/v1/intelligence/lookup", body)
    }

    fun lookupFingerprint(type: IndicatorType, fingerprint: String): ReputationResult {
        require(FINGERPRINT_REGEX.matches(fingerprint)) { "COLLECTIVE_FINGERPRINT_INVALID" }
        val body = JSONObject()
            .put("indicator_type", type.name)
            .put("indicator_fingerprint", fingerprint.lowercase())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        return executeReputation("/v1/intelligence/lookup-fingerprint", body)
    }

    fun report(
        type: IndicatorType,
        value: String,
        category: ReportCategory
    ): ReportResult {
        require(value.isNotBlank()) { "COLLECTIVE_VALUE_REQUIRED" }
        val body = JSONObject()
            .put("indicator_type", type.name)
            .put("value", value.take(MAX_RAW_VALUE_CHARS))
            .put("category", category.name)
            .put("client_nonce", UUID.randomUUID().toString())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(endpoint("/v1/intelligence/report-public"))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body)
            .build()
        val raw = executeBounded(request)
        val json = JSONObject(raw)
        return ReportResult(
            status = json.optString("status", "unknown").take(32),
            indicatorFingerprint = json.optString("indicator_fingerprint")
                .lowercase()
                .takeIf(FINGERPRINT_REGEX::matches),
            effectOnReputation = json.optString("effect_on_reputation", "unknown").take(64)
        )
    }

    private fun executeReputation(
        path: String,
        body: okhttp3.RequestBody
    ): ReputationResult {
        val request = Request.Builder()
            .url(endpoint(path))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body)
            .build()
        return parseReputation(executeBounded(request))
    }

    private fun executeBounded(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (response.request.url != request.url) {
                throw SecurityException("COLLECTIVE_ORIGIN_CHANGED")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("COLLECTIVE_HTTP_" + response.code)
            }
            val body = response.body
            val length = body.contentLength()
            if (length > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("COLLECTIVE_RESPONSE_TOO_LARGE")
            }
            val contentType = body.contentType()
            if (contentType == null || contentType.type != "application" || contentType.subtype != "json") {
                throw IllegalStateException("COLLECTIVE_CONTENT_TYPE_INVALID")
            }
            val bytes = readBounded(body.byteStream())
                ?: throw IllegalStateException("COLLECTIVE_RESPONSE_TOO_LARGE")
            return StandardCharsets.UTF_8.newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }
    }

    private fun endpoint(path: String): HttpUrl =
        buildEndpoint(baseUrl, path, allowedHosts)

    private fun readBounded(stream: java.io.InputStream): ByteArray? = stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    companion object {
        private const val PRODUCTION_HOST = "sentinel-moteur-api.onrender.com"
        private const val USER_AGENT = "SentinelQuantumVanguardAIPro-Android/1"
        private const val MAX_RAW_VALUE_CHARS = 4096
        private const val MAX_RESPONSE_BYTES = 65_536
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FINGERPRINT_REGEX = Regex("^[a-fA-F0-9]{64}$")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        internal fun buildEndpoint(
            baseUrl: String,
            path: String,
            allowedHosts: Set<String>
        ): HttpUrl {
            val parsed = runCatching { baseUrl.trim().trimEnd('/').toHttpUrl() }
                .getOrElse { throw SecurityException("COLLECTIVE_ENDPOINT_INVALID") }
            val hosts = allowedHosts.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (
                hosts.isEmpty() ||
                hosts.size > 8 ||
                parsed.scheme != "https" ||
                parsed.host.lowercase() !in hosts ||
                parsed.username.isNotEmpty() ||
                parsed.password.isNotEmpty() ||
                parsed.fragment != null ||
                !path.startsWith("/v1/intelligence/")
            ) {
                throw SecurityException("COLLECTIVE_ENDPOINT_NOT_ALLOWED")
            }
            return parsed.newBuilder()
                .encodedPath(path)
                .query(null)
                .fragment(null)
                .build()
        }

        internal fun parseReputation(raw: String): ReputationResult {
            val json = JSONObject(raw)
            val type = runCatching {
                IndicatorType.valueOf(json.getString("indicator_type"))
            }.getOrElse { throw IllegalStateException("COLLECTIVE_TYPE_INVALID") }
            val intelligence = json.optString("community_intelligence", "unknown").take(32)
            val fingerprint = if (
                !json.has("indicator_fingerprint") ||
                json.isNull("indicator_fingerprint")
            ) {
                null
            } else {
                json.optString("indicator_fingerprint").lowercase()
                    .takeIf(FINGERPRINT_REGEX::matches)
                    ?: throw IllegalStateException("COLLECTIVE_FINGERPRINT_INVALID")
            }
            if (intelligence == "available" && fingerprint == null) {
                throw IllegalStateException("COLLECTIVE_FINGERPRINT_MISSING")
            }
            val categoriesJson = json.optJSONArray("categories")
            val categories = buildList {
                if (categoriesJson != null) {
                    for (index in 0 until categoriesJson.length()) {
                        categoriesJson.optString(index)
                            .takeIf { it.isNotBlank() }
                            ?.take(64)
                            ?.let(::add)
                    }
                }
            }.distinct().take(8)
            return ReputationResult(
                indicatorType = type,
                indicatorFingerprint = fingerprint,
                riskState = json.optString("risk_state", "UNKNOWN").take(32),
                signals = json.optInt("signals", 0).coerceAtLeast(0),
                categories = categories,
                communityIntelligence = intelligence,
                reputationObservedAtMs = json.optLong("reputation_observed_at_ms", -1L)
                    .takeIf { it >= 0L },
                reputationTtlMs = json.optLong("reputation_ttl_ms", -1L)
                    .takeIf { it > 0L },
                enforcementAllowed = json.optBoolean("enforcement_allowed", false),
                warning = json.optString("warning", "").take(512)
            )
        }
    }
}
