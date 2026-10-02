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
 * Reusable HTTPS-only transport for small signed text envelopes.
 *
 * It enforces exact origin, host allowlisting, redirect refusal, bounded body
 * size, UTF-8 decoding and strict content types. It never attaches credentials.
 */
fun interface SignedEnvelopeTransport {
    fun fetch(): FetchResult

    data class FetchResult(
        val accepted: Boolean,
        val reason: String,
        val envelope: String? = null
    )
}

class OkHttpSignedEnvelopeTransport(
    endpoint: String,
    allowedHosts: Set<String>,
    private val client: OkHttpClient = defaultClient(),
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES
) : SignedEnvelopeTransport {

    private val endpoint: HttpUrl

    init {
        val parsed = endpoint.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("SIGNED_ENVELOPE_ENDPOINT_INVALID")
        val normalizedHosts = allowedHosts
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        require(normalizedHosts.isNotEmpty() && normalizedHosts.size <= MAX_ALLOWED_HOSTS) {
            "SIGNED_ENVELOPE_ALLOWED_HOSTS_INVALID"
        }
        require(parsed.isHttps && parsed.host.lowercase() in normalizedHosts) {
            "SIGNED_ENVELOPE_ENDPOINT_NOT_ALLOWED"
        }
        require(
            parsed.username.isEmpty() &&
                parsed.password.isEmpty() &&
                parsed.fragment == null
        ) {
            "SIGNED_ENVELOPE_ENDPOINT_CREDENTIALS_INVALID"
        }
        require(!client.followRedirects && !client.followSslRedirects) {
            "SIGNED_ENVELOPE_REDIRECTS_MUST_BE_DISABLED"
        }
        require(maxResponseBytes in 1..ABSOLUTE_MAX_RESPONSE_BYTES) {
            "SIGNED_ENVELOPE_RESPONSE_BOUND_INVALID"
        }

        this.endpoint = parsed
    }

    override fun fetch(): SignedEnvelopeTransport.FetchResult {
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "text/plain, application/octet-stream")
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.request.url != endpoint) {
                    return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_ORIGIN_CHANGED"
                    )
                }
                if (response.code != 200) {
                    return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_HTTP_STATUS_${response.code}"
                    )
                }

                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength < 0L || contentLength > maxResponseBytes) {
                    return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_RESPONSE_SIZE_INVALID"
                    )
                }

                val mediaType = body.contentType()
                val permittedType = mediaType != null && (
                    (mediaType.type == "text" && mediaType.subtype == "plain") ||
                        (mediaType.type == "application" && mediaType.subtype == "octet-stream")
                    )
                if (!permittedType) {
                    return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_CONTENT_TYPE_INVALID"
                    )
                }

                val bytes = readBounded(body.byteStream())
                    ?: return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_RESPONSE_TOO_LARGE"
                    )

                val envelope = runCatching {
                    StandardCharsets.UTF_8.newDecoder()
                        .decode(java.nio.ByteBuffer.wrap(bytes))
                        .toString()
                }.getOrNull()
                    ?: return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_UTF8_INVALID"
                    )

                if (envelope.isEmpty() || envelope.length > maxResponseBytes) {
                    return SignedEnvelopeTransport.FetchResult(
                        false,
                        "SIGNED_ENVELOPE_SIZE_INVALID"
                    )
                }

                SignedEnvelopeTransport.FetchResult(
                    accepted = true,
                    reason = "SIGNED_ENVELOPE_FETCH_OK",
                    envelope = envelope
                )
            }
        } catch (_: Exception) {
            SignedEnvelopeTransport.FetchResult(
                false,
                "SIGNED_ENVELOPE_NETWORK_ERROR"
            )
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
            if (total > maxResponseBytes) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_100_000
        private const val ABSOLUTE_MAX_RESPONSE_BYTES = 2_000_000
        private const val MAX_ALLOWED_HOSTS = 8

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
