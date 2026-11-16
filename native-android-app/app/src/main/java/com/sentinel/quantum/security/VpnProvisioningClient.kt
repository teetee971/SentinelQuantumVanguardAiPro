package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * HTTPS-only transport for VPN device provisioning.
 *
 * Only the selected gateway id, the device WireGuard public key and the accepted
 * signed-catalog sequence are transmitted. The WireGuard private key never enters
 * this class.
 */
class VpnProvisioningClient(
    endpointUrl: String,
    allowedHosts: Set<String>,
    private val client: OkHttpClient = defaultClient()
) {
    data class FetchResult(
        val accepted: Boolean,
        val reason: String,
        val statusCode: Int? = null,
        val responseBody: String? = null
    )

    private val endpoint: HttpUrl

    init {
        val parsed = endpointUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("VPN_PROVISIONING_ENDPOINT_INVALID")
        val hosts = allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        require(hosts.isNotEmpty() && hosts.size <= MAX_ALLOWED_HOSTS) {
            "VPN_PROVISIONING_ALLOWED_HOSTS_INVALID"
        }
        require(parsed.isHttps && parsed.host.lowercase() in hosts) {
            "VPN_PROVISIONING_ENDPOINT_NOT_ALLOWED"
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "VPN_PROVISIONING_ENDPOINT_CREDENTIALS_FORBIDDEN"
        }
        require(parsed.fragment == null && parsed.query == null) {
            "VPN_PROVISIONING_ENDPOINT_COMPONENTS_INVALID"
        }
        require(!client.followRedirects && !client.followSslRedirects) {
            "VPN_PROVISIONING_REDIRECTS_MUST_BE_DISABLED"
        }
        endpoint = parsed
    }

    fun provision(
        gatewayId: String,
        devicePublicKeyBase64: String,
        catalogSequence: Long,
        accessToken: String
    ): FetchResult {
        if (!gatewayId.matches(GATEWAY_ID_PATTERN)) {
            return FetchResult(false, "VPN_PROVISIONING_GATEWAY_ID_INVALID")
        }
        if (!VpnWireGuardIdentityStore.validWireGuardKey(devicePublicKeyBase64)) {
            return FetchResult(false, "VPN_PROVISIONING_DEVICE_KEY_INVALID")
        }
        if (catalogSequence <= 0L) {
            return FetchResult(false, "VPN_PROVISIONING_CATALOG_SEQUENCE_INVALID")
        }
        if (!validAccessToken(accessToken)) {
            return FetchResult(false, "VPN_PROVISIONING_ACCESS_TOKEN_INVALID")
        }

        val json = buildString(256) {
            append("{\"gatewayId\":")
            append(quote(gatewayId))
            append(",\"devicePublicKey\":")
            append(quote(devicePublicKeyBase64))
            append(",\"catalogSequence\":")
            append(catalogSequence)
            append("}")
        }
        if (json.length > MAX_REQUEST_CHARS) {
            return FetchResult(false, "VPN_PROVISIONING_REQUEST_TOO_LARGE")
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .header("Cache-Control", "no-store")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.request.url != endpoint) {
                    return FetchResult(false, "VPN_PROVISIONING_ORIGIN_CHANGED", response.code)
                }
                if (response.code !in 200..299) {
                    return FetchResult(false, "VPN_PROVISIONING_HTTP_${response.code}", response.code)
                }
                val body = response.body
                val length = body.contentLength()
                if (length < 0L || length > MAX_RESPONSE_BYTES) {
                    return FetchResult(false, "VPN_PROVISIONING_RESPONSE_SIZE_INVALID", response.code)
                }
                val mediaType = body.contentType()
                if (mediaType == null || mediaType.type != "application" || mediaType.subtype != "json") {
                    return FetchResult(false, "VPN_PROVISIONING_CONTENT_TYPE_INVALID", response.code)
                }
                val bytes = readBounded(body.byteStream())
                    ?: return FetchResult(false, "VPN_PROVISIONING_RESPONSE_TOO_LARGE", response.code)
                val text = runCatching {
                    StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                }.getOrNull()
                    ?: return FetchResult(false, "VPN_PROVISIONING_RESPONSE_UTF8_INVALID", response.code)
                if (text.length !in 2..MAX_RESPONSE_CHARS) {
                    return FetchResult(false, "VPN_PROVISIONING_RESPONSE_INVALID", response.code)
                }
                FetchResult(true, "VPN_PROVISIONING_FETCH_OK", response.code, text)
            }
        } catch (_: Exception) {
            FetchResult(false, "VPN_PROVISIONING_NETWORK_ERROR")
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_ALLOWED_HOSTS = 8
        private const val MAX_REQUEST_CHARS = 2048
        private const val MAX_RESPONSE_BYTES = 32 * 1024
        private const val MAX_RESPONSE_CHARS = 16_384
        private val GATEWAY_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{1,62}")
        private val ACCESS_TOKEN_PATTERN = Regex("[A-Za-z0-9._~-]{32,2048}")

        internal fun validAccessToken(value: String): Boolean =
            value == value.trim() && ACCESS_TOKEN_PATTERN.matches(value)

        internal fun quote(value: String): String = buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
