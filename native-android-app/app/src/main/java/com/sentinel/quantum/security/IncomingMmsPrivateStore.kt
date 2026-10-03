package com.sentinel.quantum.security

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Process-wide, crash-safe private persistence boundary for incoming MMS PDUs.
 *
 * Both the direct WAP delivery path and the Android download callback write the same private inbox.
 * A single synchronized boundary prevents one receiver from deleting or racing another receiver's
 * partial file. Stable full-SHA-256 names make callback replays and duplicate WAP deliveries
 * idempotent without inventing protocol identities.
 */
internal object IncomingMmsPrivateStore {
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
        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return failed()
        val targetName = IncomingMmsIdentity.persistedFileName(digest) ?: return failed()
        val partialName = IncomingMmsIdentity.partialFileName(digest) ?: return failed()

        val canonicalRoot = runCatching { filesDir.canonicalFile }.getOrNull() ?: return failed()
        if (!canonicalRoot.isDirectory) return failed()

        val directory = File(canonicalRoot, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return failed()
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return failed()
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) {
            return failed()
        }

        val target = runCatching { File(canonicalDirectory, targetName).canonicalFile }.getOrNull()
            ?: return failed()
        val partial = runCatching { File(canonicalDirectory, partialName).canonicalFile }.getOrNull()
            ?: return failed()
        if (target.parentFile != canonicalDirectory || partial.parentFile != canonicalDirectory) {
            return failed()
        }

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

        // With the process-wide monitor held, any .part is crash residue rather than a live writer.
        val residues = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == PART_EXTENSION }
            .orEmpty()
        for (residue in residues) {
            if (!runCatching { residue.delete() }.getOrDefault(false)) return failed()
        }

        val completed = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == PDU_EXTENSION }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
            .orEmpty()
        for (old in completed.drop(MAX_STORED_MMS - 1)) {
            if (!runCatching { old.delete() }.getOrDefault(false)) return failed()
        }

        if (partial.exists() && !runCatching { partial.delete() }.getOrDefault(false)) return failed()

        return try {
            FileOutputStream(partial).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            if (!partial.renameTo(target)) {
                val matchingTarget = target.isFile &&
                    target.length() == data.size.toLong() &&
                    digestFile(target) == digest
                runCatching { partial.delete() }
                if (matchingTarget) Result(State.EXISTING, digest) else failed()
            } else {
                Result(State.CREATED, digest)
            }
        } catch (_: Exception) {
            runCatching { partial.delete() }
            failed()
        }
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

    private const val DIRECTORY_NAME = "mms-inbox"
    private const val PDU_EXTENSION = "pdu"
    private const val PART_EXTENSION = "part"
    internal const val MAX_STORED_MMS = 50
}
