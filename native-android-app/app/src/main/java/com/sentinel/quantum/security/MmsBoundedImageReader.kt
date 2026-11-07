package com.sentinel.quantum.security

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

/** Reads an MMS part without allowing an unbounded provider stream into memory. */
internal object MmsBoundedImageReader {
    fun readFromProvider(openStream: () -> InputStream?, isPermissionGranted: () -> Boolean): ByteArray? =
        try {
            if (Thread.currentThread().isInterrupted || !isPermissionGranted()) null
            else openStream()?.let { read(it, isPermissionGranted = isPermissionGranted) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    fun read(
        input: InputStream,
        maxBytes: Int = MmsThumbnailPolicy.MAX_COMPRESSED_BYTES,
        isPermissionGranted: () -> Boolean = { true }
    ): ByteArray? {
        if (maxBytes <= 0) return null

        val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
        val buffer = ByteArray(minOf(maxBytes, 16 * 1024))
        var total = 0

        return try {
            while (true) {
                if (Thread.currentThread().isInterrupted || !isPermissionGranted()) return null
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) return null
                if (read > buffer.size || total > maxBytes - read) return null
                output.write(buffer, 0, read)
                total += read
            }
            if (total == 0) null else output.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { input.close() }
        }
    }
}
