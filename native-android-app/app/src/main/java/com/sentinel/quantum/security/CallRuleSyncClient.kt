package com.sentinel.quantum.security

import okhttp3.OkHttpClient

/**
 * Bounded network synchronization for signed call-reputation rules.
 *
 * This class is intentionally separate from CallScreeningService. Incoming-call screening must
 * never wait on a network request. Synchronization is expected to run from an explicit maintenance
 * path and install only packages accepted by SignedCallRulePackageVerifier.
 */
class CallRuleSyncClient(
    private val transport: CallRulePackageTransport,
    private val installer: SignedRuleInstaller
) {
    constructor(
        transport: CallRulePackageTransport,
        store: CallBlocklistStore,
        verifier: SignedCallRulePackageVerifier
    ) : this(
        transport,
        SignedRuleInstaller { envelope, now ->
            store.installSignedSilenceRules(envelope, verifier, now)
        }
    )

    fun synchronize(now: Long = System.currentTimeMillis()): SyncResult {
        if (now < 0L) return SyncResult(false, "SYNC_TIME_INVALID")
        val fetched = transport.fetch()
        if (!fetched.accepted || fetched.envelope == null) {
            return SyncResult(false, fetched.reason)
        }
        val installed = installer.install(fetched.envelope, now)
        return if (installed.accepted) {
            SyncResult(
                accepted = true,
                reason = "SYNC_RULES_INSTALLED",
                sequence = installed.rulePackage?.sequence,
                expiresAtMs = installed.rulePackage?.expiresAtMs
            )
        } else {
            SyncResult(false, installed.reason)
        }
    }

    data class SyncResult(
        val accepted: Boolean,
        val reason: String,
        val sequence: Long? = null,
        val expiresAtMs: Long? = null
    )
}

fun interface SignedRuleInstaller {
    fun install(envelope: String, now: Long): SignedCallRulePackageVerifier.Result
}

interface CallRulePackageTransport {
    fun fetch(): FetchResult

    data class FetchResult(
        val accepted: Boolean,
        val reason: String,
        val envelope: String? = null
    )
}

/**
 * Backward-compatible call-rule adapter over the shared hardened signed-envelope transport.
 */
class OkHttpCallRulePackageTransport(
    endpoint: String,
    allowedHosts: Set<String>,
    client: OkHttpClient = defaultClient()
) : CallRulePackageTransport {

    private val delegate = OkHttpSignedEnvelopeTransport(
        endpoint = endpoint,
        allowedHosts = allowedHosts,
        client = client,
        maxResponseBytes = MAX_RESPONSE_BYTES
    )

    override fun fetch(): CallRulePackageTransport.FetchResult {
        val result = delegate.fetch()
        return CallRulePackageTransport.FetchResult(
            accepted = result.accepted,
            reason = mapReason(result.reason),
            envelope = result.envelope
        )
    }

    private fun mapReason(reason: String): String = when {
        reason == "SIGNED_ENVELOPE_FETCH_OK" -> "SYNC_FETCH_OK"
        reason == "SIGNED_ENVELOPE_ORIGIN_CHANGED" -> "SYNC_ORIGIN_CHANGED"
        reason.startsWith("SIGNED_ENVELOPE_HTTP_STATUS_") ->
            reason.replaceFirst("SIGNED_ENVELOPE_", "SYNC_")
        reason == "SIGNED_ENVELOPE_RESPONSE_SIZE_INVALID" -> "SYNC_RESPONSE_SIZE_INVALID"
        reason == "SIGNED_ENVELOPE_CONTENT_TYPE_INVALID" -> "SYNC_CONTENT_TYPE_INVALID"
        reason == "SIGNED_ENVELOPE_RESPONSE_TOO_LARGE" -> "SYNC_RESPONSE_TOO_LARGE"
        reason == "SIGNED_ENVELOPE_UTF8_INVALID" -> "SYNC_RESPONSE_UTF8_INVALID"
        reason == "SIGNED_ENVELOPE_SIZE_INVALID" -> "SYNC_ENVELOPE_SIZE_INVALID"
        reason == "SIGNED_ENVELOPE_NETWORK_ERROR" -> "SYNC_NETWORK_ERROR"
        else -> "SYNC_TRANSPORT_ERROR"
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 131_072

        fun defaultClient(): OkHttpClient = OkHttpSignedEnvelopeTransport.defaultClient()
    }
}
