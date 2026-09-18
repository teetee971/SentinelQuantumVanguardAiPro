package com.sentinel.quantum.security

import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Privacy-preserving Pwned Passwords client.
 * The source password and complete SHA-1 hash never leave the device.
 */
class PwnedPasswordClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    data class Result(val exposed: Boolean, val occurrenceCount: Long)

    fun check(password: String): Result {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val hash = sha1Hex(password)
        val prefix = hash.take(PREFIX_LENGTH)
        val suffix = hash.drop(PREFIX_LENGTH)
        val request = Request.Builder()
            .url(BASE_URL + prefix)
            .header("User-Agent", "SentinelQuantumVanguardAIPro-Android/1")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP_" + response.code)
            val count = findOccurrenceCount(response.body.string(), suffix)
            return Result(exposed = count > 0L, occurrenceCount = count)
        }
    }

    companion object {
        private const val BASE_URL = "https://api.pwnedpasswords.com/range/"
        private const val PREFIX_LENGTH = 5

        fun sha1Hex(value: String): String = MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }

        fun findOccurrenceCount(rangeResponse: String, expectedSuffix: String): Long {
            val target = expectedSuffix.uppercase()
            return rangeResponse.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    val suffix = line.substring(0, separator).trim().uppercase()
                    if (suffix != target) return@mapNotNull null
                    line.substring(separator + 1).trim().toLongOrNull()
                }
                .firstOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        }
    }
}
