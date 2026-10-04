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
    private val baseUrl: String = SentinelApiOrigin.baseUrl,
    private val allowedHosts: Set<String> = SentinelApiOrigin.allowedHosts,
    private val egressGate: () -> Boolean = { false },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    enum class Category(val frenchLabel: String) {
        WANGIRI("Wangiri / appel très court"),
        SPOOFING("Usurpation du numéro"),
        PREMIUM_RATE("Numéro surtaxé"),
        ROBOCALL("Appel automatisé"),
        TELEMARKETING("Démarchage téléphonique"),
        BANK_IMPERSONATION("Faux conseiller bancaire"),
        DELIVERY_SCAM("Fausse livraison / faux colis"),
        TECH_SUPPORT_SCAM("Faux support technique"),
        GOVERNMENT_IMPERSONATION("Usurpation d’administration"),
        HARASSMENT("Harcèlement"),
        OTHER("Autre signalement")
    }

    data class Result(
        val status: String,
        val effectOnReputation: String
    )

    fun submit(
        callerNumber: String,
        recipientCountry: String,
        category: Category,
        protectionMode: ProtectionMode = ProtectionMode.LOCAL_ONLY,
        explicitConsent: Boolean = false
    ): Result {
        requireEgressAllowed(protectionMode, explicitConsent)
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
            .url(SentinelApiEndpointPolicy.build(baseUrl, PUBLIC_REPORT_PATH, allowedHosts))
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .post(payload)
            .build()

        requireDynamicEgressAllowed(egressGate)
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
        private const val PUBLIC_REPORT_PATH = "/v1/report-call-public"

        internal fun requireEgressAllowed(mode: ProtectionMode, explicitConsent: Boolean) {
            if (!ProtectionModePolicy.permitsExplicitCommunityReport(mode) || !explicitConsent) {
                throw SecurityException("COMMUNITY_REPORT_REMOTE_EGRESS_DENIED")
            }
        }

        internal fun requireDynamicEgressAllowed(gate: () -> Boolean) {
            if (!runCatching { gate() }.getOrDefault(false)) {
                throw SecurityException("COMMUNITY_REPORT_REMOTE_EGRESS_REVOKED")
            }
        }

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}
