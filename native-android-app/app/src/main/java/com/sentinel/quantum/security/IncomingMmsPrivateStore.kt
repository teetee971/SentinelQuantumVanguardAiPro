package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Idempotent app-private storage for raw incoming MMS PDUs.
 *
 * The SHA-256 fingerprint is the filename and the durable identity used by the provider journal.
 * Writes are published only after fsync through a same-directory temporary file. Existing content
 * is accepted only when its bytes still hash to the expected fingerprint.
 */
internal object IncomingMmsPrivateStore {
    sealed interface Result {
        data class Stored(
            val fingerprint: String,
            val fileName: String,
            val duplicate: Boolean
        ) : Result
        data class Rejected(val reason: String) : Result
    }

    fun store(context: Context, data: ByteArray): Result {
        if (data.isEmpty() || data.size.toLong() > MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            return Result.Rejected("MMS_PRIVATE_PDU_SIZE_INVALID")
        }

        val fingerprint = fingerprint(data)
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_PRIVATE_ROOT_UNAVAILABLE")
        val directory = File(canonicalRoot, DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("MMS_PRIVATE_DIRECTORY_FAILED")
        }
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_PRIVATE_ROOT_UNAVAILABLE")
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) {
            return Result.Rejected("MMS_PRIVATE_PATH_REJECTED")
        }

        val finalFile = File(canonicalDirectory, "$fingerprint.pdu").canonicalFile
        val temporaryFile = File(canonicalDirectory, "$fingerprint.tmp").canonicalFile
        if (finalFile.parentFile != canonicalDirectory || temporaryFile.parentFile != canonicalDirectory) {
            return Result.Rejected("MMS_PRIVATE_PATH_REJECTED")
        }

        if (finalFile.exists()) {
            val validExisting = finalFile.isFile &&
                finalFile.length() == data.size.toLong() &&
                runCatching { fingerprintFile(finalFile) == fingerprint }.getOrDefault(false)
            if (validExisting) return Result.Stored(fingerprint, finalFile.name, duplicate = true)
            if (!runCatching { finalFile.delete() }.getOrDefault(false)) {
                return Result.Rejected("MMS_PRIVATE_EXISTING_CORRUPT")
            }
        }

        prune(canonicalDirectory, keepSlots = MAX_STORED_MMS - 1)
        runCatching { temporaryFile.delete() }

        val published = runCatching {
            FileOutputStream(temporaryFile, false).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            if (temporaryFile.length() != data.size.toLong()) error("short write")
            if (fingerprintFile(temporaryFile) != fingerprint) error("fingerprint mismatch")
            if (!temporaryFile.renameTo(finalFile)) error("atomic publish failed")
            true
        }.getOrDefault(false)

        if (!published) {
            runCatching { temporaryFile.delete() }
            runCatching {
                if (finalFile.exists() && fingerprintFile(finalFile) != fingerprint) finalFile.delete()
            }
            return Result.Rejected("MMS_PRIVATE_PERSIST_FAILED")
        }

        return Result.Stored(fingerprint, finalFile.name, duplicate = false)
    }

    fun prune(context: Context): Int {
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return 0
        val directory = File(canonicalRoot, DIRECTORY)
        if (!directory.exists() || !directory.isDirectory) return 0
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return 0
        if (canonicalDirectory.parentFile != canonicalRoot) return 0
        return prune(canonicalDirectory, keepSlots = MAX_STORED_MMS)
    }

    private fun prune(directory: File, keepSlots: Int): Int {
        var deleted = 0
        directory.listFiles().orEmpty()
            .filter { it.isFile && !STORED_FILE.matches(it.name) }
            .forEach { if (runCatching { it.delete() }.getOrDefault(false)) deleted++ }

        val stored = directory.listFiles().orEmpty()
            .filter { it.isFile && STORED_FILE.matches(it.name) }
            .sortedByDescending { it.lastModified() }
        stored.drop(keepSlots.coerceAtLeast(0)).forEach {
            if (runCatching { it.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    internal fun fingerprint(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fingerprintFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    internal fun isValidStoredFileName(fileName: String): Boolean = STORED_FILE.matches(fileName)

    private const val DIRECTORY = "mms-inbox"
    internal const val MAX_STORED_MMS = 50
    private const val BUFFER_BYTES = 64 * 1024
    private val STORED_FILE = Regex("^[0-9a-f]{64}\\.pdu$")
}
