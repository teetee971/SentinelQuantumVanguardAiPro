package com.sentinel.quantum.security

import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Explicit user-submitted community report client.
 *
 * Reports go only to the public pending-moderation endpoint. No REPORT_API_KEY or other
 * server credential is embedded in the APK, and the response explicitly confirms that a
 * pending report does not change live reputation.
 */
class CommunityReportClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    enum class Category {
        WANGIRI,
        SPOOFING,
        PREMIUM_RATE,
        ROBOCALL,
        OTHER
    }

    data class Result(
        val status: String,
        val effectOnReputation: String
    )

    fun submit(
        callerNumber: String,
        recipientCountry: String,
        category: Category,
        privacyMode: PhonePrivacyFirewall.Mode = PhonePrivacyFirewall.Mode.LOCAL_ONLY,
        explicitConsent: Boolean = false
    ): Result {
        requireEgressAllowed(privacyMode, explicitConsent)
        val normalized = CallRuleEngine.normalizeNumber(callerNumber)
            ?: throw IllegalArgumentException("Invalid caller number")

        val payload = JSONObject()
            .put("caller_number", normalized)
            .put("recipient_country", recipientCountry.uppercase().take(2).ifBlank { "FR" })
            .put("category", category.name)
            .put("client_nonce", UUID.randomUUID().toString())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .post(payload)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP_" + response.code)
            }
            val json = JSONObject(response.body.string())
            return Result(
                status = json.optString("status", "unknown").take(32),
                effectOnReputation = json.optString(
                    "effect_on_reputation",
                    "unknown"
                ).take(64)
            )
        }
    }

    companion object {
        internal fun requireEgressAllowed(
            mode: PhonePrivacyFirewall.Mode,
            explicitConsent: Boolean
        ) {
            val decision = PhonePrivacyFirewall.decide(
                mode, PhonePrivacyFirewall.DataClass.PHONE_NUMBER, explicitConsent
            )
            if (!decision.mayLeaveDevice) {
                throw SecurityException("COMMUNITY_REPORT_REMOTE_EGRESS_DENIED")
            }
        }

        const val ENDPOINT =
            "https://sentinel-moteur-api.onrender.com/v1/report-call-public"
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}
