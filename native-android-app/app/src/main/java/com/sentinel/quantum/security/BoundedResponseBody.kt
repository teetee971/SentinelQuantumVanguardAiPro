package com.sentinel.quantum.security

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import okhttp3.ResponseBody

/** Reads remote response bodies with a hard byte bound, including chunked responses. */
internal object BoundedResponseBody {
    fun read(body: ResponseBody, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) throw IllegalStateException("HTTP_RESPONSE_TOO_LARGE")

        val output = ByteArrayOutputStream(
            if (declaredLength in 1L..maxBytes.toLong()) declaredLength.toInt()
            else minOf(maxBytes, 8192)
        )
        val buffer = ByteArray(8192)
        var total = 0
        body.source().use { source ->
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw IllegalStateException("HTTP_RESPONSE_TOO_LARGE")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    fun readText(body: ResponseBody, maxBytes: Int): String =
        String(read(body, maxBytes), StandardCharsets.UTF_8)
}
