package com.sentinel.quantum.security

import java.io.File
import java.io.IOException
import java.io.InputStream

/** Reads exactly the measured PDU length and rejects an unfinished, truncated or growing file. */
internal object IncomingMmsPduFileReader {
    const val MAX_BYTES = 17L * 1024L * 1024L

    fun read(file: File, expectedLength: Long): ByteArray? {
        if (expectedLength !in 1..MAX_BYTES) return null
        return try {
            file.inputStream().use { readExact(it, expectedLength.toInt()) }
                ?.takeIf { file.length() == expectedLength }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    internal fun readExact(input: InputStream, expectedLength: Int): ByteArray? {
        if (expectedLength.toLong() !in 1..MAX_BYTES) return null
        val data = ByteArray(expectedLength)
        var offset = 0
        while (offset < expectedLength) {
            val count = input.read(data, offset, expectedLength - offset)
            if (count <= 0) return null
            offset += count
        }
        return data.takeIf { input.read() == -1 }
    }
}
