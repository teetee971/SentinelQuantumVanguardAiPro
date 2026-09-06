package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

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
        SignedRuleInstaller { envelope, now -> store.installSignedSilenceRules(envelope, verifier, now) }
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
 * HTTPS-only transport with exact-origin, redirect, content-type, timeout and response-size bounds.
 * No credentials are attached by this transport.
 */
class OkHttpCallRulePackageTransport(
    endpoint: String,
    allowedHosts: Set<String>,
    private val client: OkHttpClient = defaultClient()
) : CallRulePackageTransport {
    private val endpoint: HttpUrl

    init {
        val parsed = endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("SYNC_ENDPOINT_INVALID")
        val normalizedHosts = allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        require(normalizedHosts.isNotEmpty() && normalizedHosts.size <= MAX_ALLOWED_HOSTS) { "SYNC_ALLOWED_HOSTS_INVALID" }
        require(parsed.isHttps && parsed.host.lowercase() in normalizedHosts) { "SYNC_ENDPOINT_NOT_ALLOWED" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment == null) { "SYNC_ENDPOINT_CREDENTIALS_INVALID" }
        require(!client.followRedirects && !client.followSslRedirects) { "SYNC_REDIRECTS_MUST_BE_DISABLED" }
        this.endpoint = parsed
    }

    override fun fetch(): CallRulePackageTransport.FetchResult {
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "text/plain")
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.request.url != endpoint) {
                    return CallRulePackageTransport.FetchResult(false, "SYNC_ORIGIN_CHANGED")
                }
                if (response.code != 200) {
                    return CallRulePackageTransport.FetchResult(false, "SYNC_HTTP_STATUS_${response.code}")
                }
                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength < 0L || contentLength > MAX_RESPONSE_BYTES) {
                    return CallRulePackageTransport.FetchResult(false, "SYNC_RESPONSE_SIZE_INVALID")
                }
                val mediaType = body.contentType()
                val permittedType = mediaType != null && (
                    (mediaType.type == "text" && mediaType.subtype == "plain") ||
                        (mediaType.type == "application" && mediaType.subtype == "octet-stream")
                    )
                if (!permittedType) {
                    return CallRulePackageTransport.FetchResult(false, "SYNC_CONTENT_TYPE_INVALID")
                }
                val bytes = readBounded(body.byteStream())
                    ?: return CallRulePackageTransport.FetchResult(false, "SYNC_RESPONSE_TOO_LARGE")
                val envelope = runCatching {
                    StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                }.getOrNull() ?: return CallRulePackageTransport.FetchResult(false, "SYNC_RESPONSE_UTF8_INVALID")
                if (envelope.isEmpty() || envelope.length > MAX_ENVELOPE_CHARS) {
                    return CallRulePackageTransport.FetchResult(false, "SYNC_ENVELOPE_SIZE_INVALID")
                }
                CallRulePackageTransport.FetchResult(true, "SYNC_FETCH_OK", envelope)
            }
        } catch (_: Exception) {
            CallRulePackageTransport.FetchResult(false, "SYNC_NETWORK_ERROR")
        }
    }

    private fun readBounded(stream: InputStream): ByteArray? = stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
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
        private const val MAX_ALLOWED_HOSTS = 8
        private const val MAX_RESPONSE_BYTES = 131_072
        private const val MAX_ENVELOPE_CHARS = 131_072

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
