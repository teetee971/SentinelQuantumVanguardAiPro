package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

interface VpnGatewayCatalogTransport {
    fun fetch(): FetchResult

    data class FetchResult(
        val accepted: Boolean,
        val reason: String,
        val envelope: String? = null
    )
}

/**
 * HTTPS-only transport for the signed Sentinel VPN gateway catalog.
 *
 * No credential is attached. Authenticity, freshness and anti-rollback are enforced
 * separately by SignedVpnGatewayCatalogVerifier.
 */
class OkHttpVpnGatewayCatalogTransport(
    endpointUrl: String,
    allowedHosts: Set<String>,
    private val client: OkHttpClient = defaultClient()
) : VpnGatewayCatalogTransport {
    private val endpoint: HttpUrl

    init {
        val parsed = endpointUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("VPN_CATALOG_ENDPOINT_INVALID")
        val hosts = allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        require(hosts.isNotEmpty() && hosts.size <= MAX_ALLOWED_HOSTS) {
            "VPN_CATALOG_ALLOWED_HOSTS_INVALID"
        }
        require(parsed.isHttps && parsed.host.lowercase() in hosts) {
            "VPN_CATALOG_ENDPOINT_NOT_ALLOWED"
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "VPN_CATALOG_ENDPOINT_CREDENTIALS_FORBIDDEN"
        }
        require(parsed.fragment == null && parsed.query == null) {
            "VPN_CATALOG_ENDPOINT_COMPONENTS_INVALID"
        }
        require(!client.followRedirects && !client.followSslRedirects) {
            "VPN_CATALOG_REDIRECTS_MUST_BE_DISABLED"
        }
        endpoint = parsed
    }

    override fun fetch(): VpnGatewayCatalogTransport.FetchResult {
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "text/plain")
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.request.url != endpoint) {
                    return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_ORIGIN_CHANGED"
                    )
                }
                if (response.code != 200) {
                    return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_HTTP_${response.code}"
                    )
                }

                val body = response.body
                val length = body.contentLength()
                if (length < 0L || length > MAX_RESPONSE_BYTES) {
                    return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_RESPONSE_SIZE_INVALID"
                    )
                }

                val mediaType = body.contentType()
                val permitted = mediaType != null && (
                    (mediaType.type == "text" && mediaType.subtype == "plain") ||
                        (mediaType.type == "application" && mediaType.subtype == "octet-stream")
                    )
                if (!permitted) {
                    return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_CONTENT_TYPE_INVALID"
                    )
                }

                val bytes = readBounded(body.byteStream())
                    ?: return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_RESPONSE_TOO_LARGE"
                    )
                val envelope = runCatching {
                    StandardCharsets.UTF_8.newDecoder()
                        .decode(java.nio.ByteBuffer.wrap(bytes))
                        .toString()
                }.getOrNull()
                    ?: return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_RESPONSE_UTF8_INVALID"
                    )

                if (envelope.length !in 1..MAX_ENVELOPE_CHARS) {
                    return VpnGatewayCatalogTransport.FetchResult(
                        false,
                        "VPN_CATALOG_ENVELOPE_SIZE_INVALID"
                    )
                }

                VpnGatewayCatalogTransport.FetchResult(
                    true,
                    "VPN_CATALOG_FETCH_OK",
                    envelope
                )
            }
        } catch (_: Exception) {
            VpnGatewayCatalogTransport.FetchResult(
                false,
                "VPN_CATALOG_NETWORK_ERROR"
            )
        }
    }

    private fun readBounded(stream: InputStream): ByteArray? = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

    companion object {
        private const val MAX_ALLOWED_HOSTS = 8
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val MAX_ENVELOPE_CHARS = 262_144

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
