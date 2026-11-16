package com.sentinel.quantum.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most [maxBytes] and returns null if the stream exceeds that bound. */
internal object BoundedInputReader {
    fun read(input: InputStream, maxBytes: Int): ByteArray? {
        require(maxBytes >= 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) return null
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
