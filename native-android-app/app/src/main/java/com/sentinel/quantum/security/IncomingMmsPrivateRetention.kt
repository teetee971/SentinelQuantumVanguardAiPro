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
        val current = readUnlocked(filesDir)
        val previous = current.firstOrNull { it.digest == normalized }?.subscriptionId
        val subscription = subscriptionId?.takeIf { it >= 0 } ?: previous
        if (current.any { it.digest == normalized && it.subscriptionId == subscription }) {
            return@synchronized true
        }
        val entries = current.filterNot { it.digest == normalized } + Entry(normalized, subscription)
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
        val directory = inbox(filesDir) ?: return emptyList()
        val main = File(directory, FILE_NAME)
        val backup = File(directory, BACKUP_NAME)
        if (!main.isFile && backup.isFile) {
            runCatching { backup.renameTo(main) }
        }
        if (!main.isFile) return emptyList()
        return runCatching { main.readText() }.getOrNull()
            .orEmpty()
            .lineSequence()
            .mapNotNull(::parse)
            .distinctBy { it.digest }
            .toList()
    }

    private fun write(filesDir: File, entries: List<Entry>): Boolean {
        val directory = inbox(filesDir) ?: return false
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.${java.util.UUID.randomUUID()}.tmp")
        val backup = File(directory, BACKUP_NAME)
        val payload = entries.joinToString(separator = "\n") { entry ->
            entry.subscriptionId?.let { "${entry.digest} $it" } ?: entry.digest
        }
        val streamed = runCatching {
            FileOutputStream(temporary).use { stream ->
                stream.write(payload.toByteArray(Charsets.US_ASCII))
                stream.fd.sync()
            }
            true
        }.getOrDefault(false)
        if (!streamed) {
            runCatching { temporary.delete() }
            return false
        }
        if (!target.exists()) {
            val created = temporary.renameTo(target)
            if (!created) runCatching { temporary.delete() }
            return created && target.isFile
        }
        if (backup.exists() && !backup.delete()) {
            runCatching { temporary.delete() }
            return false
        }
        if (!target.renameTo(backup)) {
            runCatching { temporary.delete() }
            return target.isFile
        }
        if (!temporary.renameTo(target)) {
            if (!backup.renameTo(target) && !target.isFile && backup.isFile) {
                runCatching { backup.renameTo(target) }
            }
            runCatching { temporary.delete() }
            return false
        }
        runCatching { backup.delete() }
        return target.isFile
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
    private const val BACKUP_NAME = "retention-v1.bak"
    private val LOCK = Any()
}
