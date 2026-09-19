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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    data class Result(
        val riskScore: Int,
        val action: String,
        val flags: List<String>,
        val signals: Int,
        val callerCountry: String?,
        val isInternational: Boolean?,
        val communityIntelligence: String,
        val warning: String
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
            .url(ENDPOINT)
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .post(body)
            .build()

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

        const val ENDPOINT = "https://sentinel-moteur-api.onrender.com/v1/evaluate-call"
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
            return Result(
                riskScore = payload.optInt("risk_score", 0).coerceIn(0, 100),
                action = payload.optString("action", "UNKNOWN").take(32),
                flags = flags,
                signals = payload.optInt("signals", 0).coerceAtLeast(0),
                callerCountry = payload.optString("caller_country")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.take(8),
                isInternational = if (payload.has("is_international") && !payload.isNull("is_international")) {
                    payload.optBoolean("is_international")
                } else null,
                communityIntelligence = payload.optString("community_intelligence", "unknown").take(32),
                warning = payload.optString("warning", "").take(512)
            )
        }
    }
}
