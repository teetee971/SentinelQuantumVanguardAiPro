package com.sentinel.quantum.telecom

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Bounded production transport for the read-only official-source probe. It is intentionally
 * usable only from a foreground/worker path; the Telecom callback never constructs it.
 */
class OkHttpOfficialSourceTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(java.time.Duration.ofSeconds(10))
        .build()
) : OfficialSourceTransport {
    override fun fetch(officialUrl: String, maxBytes: Int): TransportResponse {
        require(maxBytes in 1..MAX_ALLOWED_BYTES)
        val request = Request.Builder()
            .url(officialUrl)
            .header("Accept", "text/html, application/json, application/xml;q=0.8")
            .header("User-Agent", "SentinelQuantumVanguard/telecom-probe")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = readBounded(response.body.byteStream(), maxBytes)
            return TransportResponse(
                statusCode = response.code,
                contentType = response.body.contentType()?.toString(),
                body = body
            )
        }
    }

    internal companion object {
        private const val MAX_ALLOWED_BYTES = 2 * 1024 * 1024
        private const val BUFFER_SIZE = 8 * 1024

        /** Returns maxBytes + 1 bytes as an explicit oversize sentinel. */
        fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
            require(maxBytes in 1..MAX_ALLOWED_BYTES)
            val output = ByteArrayOutputStream(maxBytes.coerceAtMost(BUFFER_SIZE))
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            input.use { stream ->
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (total > maxBytes - count) return ByteArray(maxBytes + 1)
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return output.toByteArray()
        }
    }
}
