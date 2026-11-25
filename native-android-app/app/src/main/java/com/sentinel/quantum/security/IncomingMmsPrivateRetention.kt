package com.sentinel.quantum.security

import java.io.File
import java.io.FileOutputStream

/**
 * Durable pin for a private MMS that is still the only copy.
 * A projected message is removed from this file and may then leave the quota.
 * This file never authorizes deleting a PDU.
 */
internal object IncomingMmsPrivateRetention {
    data class Entry(val digest: String, val subscriptionId: Int?)

    fun read(filesDir: File): List<Entry> = synchronized(LOCK) { readUnlocked(filesDir) }

    fun contains(filesDir: File, digest: String): Boolean =
        read(filesDir).any { it.digest == digest.lowercase() }

    fun retain(filesDir: File, digest: String, subscriptionId: Int?): Boolean = synchronized(LOCK) {
        val normalized = digest.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalized) == null) return@synchronized false
        val subscription = subscriptionId?.takeIf { it >= 0 }
        val entries = readUnlocked(filesDir).filterNot { it.digest == normalized } +
            Entry(normalized, subscription)
        write(filesDir, entries)
    }

    fun clearSubscription(filesDir: File, digest: String): Boolean = synchronized(LOCK) {
        val normalized = digest.lowercase()
        val current = readUnlocked(filesDir)
        if (current.none { it.digest == normalized }) return@synchronized true
        write(
            filesDir,
            current.map { if (it.digest == normalized) it.copy(subscriptionId = null) else it }
        )
    }

    fun release(filesDir: File, digest: String): Boolean = synchronized(LOCK) {
        val normalized = digest.lowercase()
        val current = readUnlocked(filesDir)
        if (current.none { it.digest == normalized }) return@synchronized true
        write(filesDir, current.filterNot { it.digest == normalized })
    }

    private fun readUnlocked(filesDir: File): List<Entry> {
        val file = ledgerFile(filesDir) ?: return emptyList()
        if (!file.isFile) return emptyList()
        return runCatching { file.readText() }.getOrNull()
            .orEmpty()
            .lineSequence()
            .mapNotNull(::parse)
            .distinctBy { it.digest }
            .toList()
    }

    private fun write(filesDir: File, entries: List<Entry>): Boolean {
        val directory = inbox(filesDir) ?: return false
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")
        val payload = entries.joinToString(separator = "\n") { entry ->
            entry.subscriptionId?.let { "${entry.digest} $it" } ?: entry.digest
        }
        val written = runCatching {
            FileOutputStream(temporary).use { stream ->
                stream.write(payload.toByteArray(Charsets.US_ASCII))
                stream.fd.sync()
            }
            if (target.exists() && !target.delete()) return@runCatching false
            temporary.renameTo(target)
        }.getOrDefault(false)
        if (!written) runCatching { temporary.delete() }
        return written && target.isFile
    }

    private fun ledgerFile(filesDir: File): File? {
        val directory = inbox(filesDir) ?: return null
        val file = runCatching { File(directory, FILE_NAME).canonicalFile }.getOrNull() ?: return null
        if (file.parentFile != directory) return null
        return file
    }

    private fun inbox(filesDir: File): File? {
        val root = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        if (!root.isDirectory && !root.mkdirs()) return null
        val directory = File(root, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null
        val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (canonical.parentFile != root || !canonical.isDirectory) return null
        return canonical
    }

    private fun parse(line: String): Entry? {
        val parts = line.trim().split(' ')
        if (parts.isEmpty() || IncomingMmsIdentity.persistedFileName(parts[0]) == null) return null
        if (parts.size == 1) return Entry(parts[0], null)
        if (parts.size != 2) return null
        val subscription = parts[1].toIntOrNull() ?: return null
        if (subscription < 0) return null
        return Entry(parts[0], subscription)
    }

    private const val DIRECTORY_NAME = "mms-inbox"
    private const val FILE_NAME = "retention-v1"
    private val LOCK = Any()
}
