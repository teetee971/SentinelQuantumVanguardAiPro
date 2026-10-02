package com.sentinel.quantum.security

import java.io.InputStream
import java.security.MessageDigest

/** Small streaming SHA-256 helper for security artifacts. */
object SentinelSha256 {

    fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun normalize(value: String): String? {
        val normalized = value.trim().lowercase()
        return normalized.takeIf {
            it.length == SHA256_HEX_LENGTH && it.all { char -> char in '0'..'9' || char in 'a'..'f' }
        }
    }

    private const val SHA256_HEX_LENGTH = 64
}
