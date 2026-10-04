package com.sentinel.quantum.security

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional, post-screening enrichment. Never call this from CallScreeningService before
 * respondToCall(). The caller number is transmitted only when the user has explicitly enabled
 * remote enrichment in SettingsStore.
 */
class CallerReputationClient(
    private val endpointBaseUrl: String = SentinelApiOrigin.baseUrl,
    private val egressGate: () -> Boolean = { false },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    data class Result(
        val riskScore: Int?,
        val action: String,
        val flags: List<String>,
        val categories: List<String>,
        val signals: Int,
        val callerCountry: String?,
        val isInternational: Boolean?,
        val communityIntelligence: String,
        val warning: String,
        val reputationObservedAtMs: Long?,
        val reputationTtlMs: Long?
    )

    fun evaluate(
        callerNumber: String,
        recipientCountry: String,
        verificationStatus: String,
        privacyMode: PhonePrivacyFirewall.Mode = PhonePrivacyFirewall.Mode.LOCAL_ONLY,
        explicitConsent: Boolean = false
    ): Result {
        requireEgressAllowed(privacyMode, explicitConsent)
        val normalized = CallRuleEngine.normalizeNumber(callerNumber)
            ?: throw IllegalArgumentException("Invalid caller number")
        val body = JSONObject()
            .put("caller_number", normalized)
            .put("recipient_country", recipientCountry.uppercase().take(2).ifBlank { "FR" })
            .put("ring_duration_ms", JSONObject.NULL)
            .put("verification_status", verificationStatus.take(64))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(endpoint(endpointBaseUrl))
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .post(body)
            .build()

        requireDynamicEgressAllowed(egressGate)
        client.newCall(request).execute().use { httpResponse ->
            if (!httpResponse.isSuccessful) throw IllegalStateException("HTTP_" + httpResponse.code)
            return parseResponse(httpResponse.body.string())
        }
    }

    companion object {
        internal fun requireEgressAllowed(
            mode: PhonePrivacyFirewall.Mode,
            explicitConsent: Boolean
        ) {
            val number = PhonePrivacyFirewall.decide(mode, PhonePrivacyFirewall.DataClass.PHONE_NUMBER, explicitConsent)
            val reputation = PhonePrivacyFirewall.decide(mode, PhonePrivacyFirewall.DataClass.REPUTATION_QUERY, explicitConsent)
            if (!number.mayLeaveDevice || !reputation.mayLeaveDevice) {
                throw SecurityException("PHONE_CORE_REMOTE_EGRESS_DENIED")
            }
        }

        internal fun requireDynamicEgressAllowed(gate: () -> Boolean) {
            if (!runCatching { gate() }.getOrDefault(false)) {
                throw SecurityException("PHONE_CORE_REMOTE_EGRESS_REVOKED")
            }
        }

        internal fun endpoint(baseUrl: String): String =
            SentinelApiEndpointPolicy.build(
                baseUrl,
                "/v1/evaluate-call",
                SentinelApiOrigin.allowedHosts
            ).toString()

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun parseResponse(raw: String): Result {
            val payload = JSONObject(raw)
            val flagsJson = payload.optJSONArray("flags")
            val flags = buildList {
                if (flagsJson != null) {
                    for (index in 0 until flagsJson.length()) {
                        flagsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.take(12)
            val categoriesJson = payload.optJSONArray("categories")
            val categories = buildList {
                if (categoriesJson != null) {
                    for (index in 0 until categoriesJson.length()) {
                        categoriesJson.optString(index)
                            .takeIf { it.isNotBlank() }
                            ?.take(64)
                            ?.let(::add)
                    }
                }
            }.distinct().take(6)
            return Result(
                riskScore = payload.optInt("risk_score", -1).takeIf { it in 0..100 },
                action = payload.optString("action", "UNKNOWN").take(32),
                flags = flags,
                categories = categories,
                signals = payload.optInt("signals", 0).coerceAtLeast(0),
                callerCountry = payload.optString("caller_country")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.take(8),
                isInternational = if (payload.has("is_international") && !payload.isNull("is_international")) {
                    payload.optBoolean("is_international")
                } else null,
                communityIntelligence = payload.optString("community_intelligence", "unknown").take(32),
                warning = payload.optString("warning", "").take(512),
                reputationObservedAtMs = payload.optLong("reputation_observed_at_ms", -1L)
                    .takeIf { it >= 0L },
                reputationTtlMs = payload.optLong("reputation_ttl_ms", -1L)
                    .takeIf { it > 0L }
            )
        }
    }
}
