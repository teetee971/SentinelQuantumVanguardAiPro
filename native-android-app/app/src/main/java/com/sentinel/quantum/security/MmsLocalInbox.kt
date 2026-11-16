package com.sentinel.quantum.security

import java.io.File

/**
 * Read-only, bounded index of raw MMS PDUs already accepted by SentinelMmsDeliverReceiver.
 * This deliberately does not parse or execute untrusted MMS payloads.
 */
object MmsLocalInbox {
    data class Item(
        val name: String,
        val receivedAtMs: Long,
        val sizeBytes: Long
    )

    fun list(directory: File, limit: Int = 50): List<Item> {
        val safeLimit = limit.coerceIn(1, 50)
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return emptyList()
        if (!canonicalDirectory.isDirectory) return emptyList()
        return canonicalDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "pdu" }
            .filter { file ->
                runCatching { file.canonicalFile.parentFile == canonicalDirectory }.getOrDefault(false)
            }
            .filter { it.length() in 1..MAX_PDU_BYTES }
            .sortedByDescending { it.lastModified() }
            .take(safeLimit)
            .map { Item(it.name.take(128), it.lastModified().coerceAtLeast(0L), it.length()) }
            .toList()
    }

    private const val MAX_PDU_BYTES = 17L * 1024L * 1024L
}
