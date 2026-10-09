package com.sentinel.quantum.security

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Crash-safe private staging for a WAP PDU captured before the delivery worker can run.
 *
 * This store is separate from the downloaded-MMS inbox so normal callback traffic cannot evict a
 * journaled WAP ingress before its replay worker has had a chance to consume it.
 */
internal object IncomingMmsWapIngressStore {
    enum class State {
        CREATED,
        EXISTING,
        FAILED
    }

    data class Result(
        val state: State,
        val digestHex: String?
    )

    @Synchronized
    fun persist(filesDir: File, data: ByteArray): Result {
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return failed()
        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return failed()
        val targetName = "$digest.$PDU_EXTENSION"
        val partialName = "$digest.$PART_EXTENSION"
        val directory = directory(filesDir) ?: return failed()
        val target = safeChild(directory, targetName) ?: return failed()
        val partial = safeChild(directory, partialName) ?: return failed()

        if (target.exists()) {
            return if (
                target.isFile &&
                    target.length() == data.size.toLong() &&
                    digestFile(target) == digest
            ) {
                Result(State.EXISTING, digest)
            } else {
                failed()
            }
        }

        val completed = directory.listFiles()
            ?.filter { it.isFile && it.extension == PDU_EXTENSION }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
            .orEmpty()
        for (old in completed.drop(MAX_STORED_WAP - 1)) {
            if (!runCatching { old.delete() }.getOrDefault(false)) return failed()
        }
        if (partial.exists() && !runCatching { partial.delete() }.getOrDefault(false)) {
            return failed()
        }

        return try {
            FileOutputStream(partial).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            if (partial.renameTo(target)) {
                Result(State.CREATED, digest)
            } else {
                val matchingTarget = target.isFile &&
                    target.length() == data.size.toLong() &&
                    digestFile(target) == digest
                runCatching { partial.delete() }
                if (matchingTarget) Result(State.EXISTING, digest) else failed()
            }
        } catch (_: Exception) {
            runCatching { partial.delete() }
            failed()
        }
    }

    @Synchronized
    fun read(filesDir: File, digestHex: String): ByteArray? {
        val normalizedDigest = digestHex.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalizedDigest) == null) return null
        val directory = directory(filesDir, create = false) ?: return null
        val target = safeChild(directory, "$normalizedDigest.$PDU_EXTENSION") ?: return null
        if (!target.isFile || target.length() !in 1..MAX_PDU_BYTES) return null
        return runCatching {
            target.readBytes().takeIf { IncomingMmsIdentity.sha256Hex(it) == normalizedDigest }
        }.getOrNull()
    }

    @Synchronized
    fun delete(filesDir: File, digestHex: String): Boolean {
        val normalizedDigest = digestHex.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalizedDigest) == null) return false
        val directory = directory(filesDir, create = false) ?: return true
        val target = safeChild(directory, "$normalizedDigest.$PDU_EXTENSION") ?: return false
        val partial = safeChild(directory, "$normalizedDigest.$PART_EXTENSION") ?: return false
        return (!target.exists() || runCatching { target.delete() }.getOrDefault(false)) &&
            (!partial.exists() || runCatching { partial.delete() }.getOrDefault(false))
    }

    private fun directory(filesDir: File, create: Boolean = true): File? {
        val canonicalRoot = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        if (!canonicalRoot.isDirectory) return null
        val rawDirectory = File(canonicalRoot, DIRECTORY_NAME)
        if (create && !rawDirectory.exists() && !rawDirectory.mkdirs()) return null
        val canonicalDirectory = runCatching { rawDirectory.canonicalFile }.getOrNull() ?: return null
        return canonicalDirectory.takeIf {
            it.parentFile == canonicalRoot && it.isDirectory
        }
    }

    private fun safeChild(directory: File, name: String): File? {
        val child = runCatching { File(directory, name).canonicalFile }.getOrNull() ?: return null
        return child.takeIf { it.parentFile == directory }
    }

    private fun digestFile(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }.getOrNull()

    private fun failed() = Result(State.FAILED, null)

    private const val DIRECTORY_NAME = "mms-wap-inbox"
    private const val PDU_EXTENSION = "pdu"
    private const val PART_EXTENSION = "part"
    private const val MAX_PDU_BYTES = 512 * 1024
    internal const val MAX_STORED_WAP = 64
}
