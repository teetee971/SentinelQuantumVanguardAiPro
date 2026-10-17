package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Authenticated Digital Exposure client. The Auth0 access token remains in memory and is sent only
 * in the Authorization header. The backend derives the opaque subject identity; Android never sends
 * a subject_token or the server-to-server X-Exposure-Key.
 */
class DigitalExposureClient(
    private val endpointBaseUrl: String = SentinelApiOrigin.baseUrl,
    private val allowedHosts: Set<String> = SentinelApiOrigin.allowedHosts,
    private val egressGate: () -> Boolean = { false },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    data class Indicator(val type: String, val value: String)

    data class LookupResult(
        val exposureIntelligence: String,
        val matchState: String,
        val matchesCount: Int
    )

    fun lookup(
        accessToken: String,
        indicators: List<Indicator>,
        privacyMode: PhonePrivacyFirewall.Mode = PhonePrivacyFirewall.Mode.LOCAL_ONLY,
        explicitConsent: Boolean = false
    ): LookupResult {
        requireEgressAllowed(privacyMode, explicitConsent)
        val request = Request.Builder()
            .url(endpoint(endpointBaseUrl, allowedHosts))
            .header("Authorization", bearerHeader(accessToken))
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .post(buildLookupBody(indicators).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        requireDynamicEgressAllowed(egressGate)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP_" + response.code)
            val result = parseLookupResponse(response.body.string())
            IntegrationTruthRuntime.publishConnected(IntegrationCapabilityId.DIGITAL_EXPOSURE)
            return result
        }
    }

    companion object {
        private val ALLOWED_INDICATOR_TYPES = setOf("DOMAIN", "URL", "EMAIL", "SHA256")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun requireEgressAllowed(
            mode: PhonePrivacyFirewall.Mode,
            explicitConsent: Boolean
        ) {
            val decision = PhonePrivacyFirewall.decide(
                mode,
                PhonePrivacyFirewall.DataClass.EXPOSURE_QUERY,
                explicitConsent
            )
            if (!decision.mayLeaveDevice) {
                throw SecurityException("DIGITAL_EXPOSURE_REMOTE_EGRESS_DENIED")
            }
        }

        internal fun requireDynamicEgressAllowed(gate: () -> Boolean) {
            if (!runCatching { gate() }.getOrDefault(false)) {
                throw SecurityException("DIGITAL_EXPOSURE_REMOTE_EGRESS_REVOKED")
            }
        }

        internal fun bearerHeader(accessToken: String): String {
            val token = accessToken.trim()
            require(token.isNotEmpty() && token.length <= 8_192) { "Invalid access token" }
            require(token == accessToken && token.none { it.isWhitespace() || it == '\r' || it == '\n' }) {
                "Invalid access token"
            }
            return "Bearer $token"
        }

        internal fun buildLookupBody(indicators: List<Indicator>): String {
            require(indicators.size in 1..50) { "Indicator count must be between 1 and 50" }
            val serialized = JSONArray()
            indicators.forEach { indicator ->
                val type = indicator.type.trim().uppercase()
                val value = indicator.value.trim()
                require(type in ALLOWED_INDICATOR_TYPES) { "Unsupported indicator type" }
                require(value.isNotEmpty() && value.length <= 2_048) { "Invalid indicator value" }
                serialized.put(
                    JSONObject()
                        .put("indicator_type", type)
                        .put("value", value)
                )
            }
            return JSONObject().put("indicators", serialized).toString()
        }

        internal fun endpoint(baseUrl: String, allowedHosts: Set<String>): String =
            SentinelApiEndpointPolicy.buildAuthenticated(
                baseUrl,
                "/v1/intelligence/exposures/lookup-user",
                allowedHosts
            ).toString()

        internal fun parseLookupResponse(raw: String): LookupResult {
            val payload = JSONObject(raw)
            if (payload.optBoolean("enforcement_allowed", true)) {
                throw IllegalStateException("DIGITAL_EXPOSURE_RESPONSE_INVALID")
            }
            val matchState = payload.optString("match_state", "")
            if (matchState !in setOf("UNAVAILABLE", "MATCH", "NO_MATCH")) {
                throw IllegalStateException("DIGITAL_EXPOSURE_RESPONSE_INVALID")
            }
            val intelligence = payload.optString("exposure_intelligence", "").take(32)
            if (intelligence.isBlank()) {
                throw IllegalStateException("DIGITAL_EXPOSURE_RESPONSE_INVALID")
            }
            val matches = payload.optJSONArray("matches")
                ?: throw IllegalStateException("DIGITAL_EXPOSURE_RESPONSE_INVALID")
            return LookupResult(
                exposureIntelligence = intelligence,
                matchState = matchState,
                matchesCount = matches.length().coerceAtMost(50)
            )
        }
    }
}
