package com.sentinel.quantum.security

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stages an already-composed MMS PDU in the app-private cache for Android's public MMS API.
 * This component never submits a message to telephony.
 */
object MmsSendPduStager {
    sealed class Result {
        data class Staged(val token: String, val fileName: String, val contentUri: Uri) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun stage(context: Context, pdu: ByteArray): Result {
        if (pdu.isEmpty() || pdu.size.toLong() > MAX_STAGED_PDU_BYTES) {
            return Result.Rejected("INVALID_MMS_PDU_SIZE")
        }

        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        val directory = File(canonicalCache, SEND_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("MMS_SEND_DIRECTORY_FAILED")
        }
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        if (canonicalDirectory.parentFile != canonicalCache) {
            return Result.Rejected("MMS_CACHE_PATH_REJECTED")
        }
        prune(canonicalDirectory)

        val token = UUID.randomUUID().toString()
        val finalFile = File(canonicalDirectory, "$token.pdu").canonicalFile
        val temporaryFile = File(canonicalDirectory, "$token.tmp").canonicalFile
        if (finalFile.parentFile != canonicalDirectory || temporaryFile.parentFile != canonicalDirectory) {
            return Result.Rejected("MMS_CACHE_PATH_REJECTED")
        }

        val written = runCatching {
            FileOutputStream(temporaryFile, false).use { stream ->
                stream.write(pdu)
                stream.fd.sync()
            }
            if (temporaryFile.length() != pdu.size.toLong()) error("short write")
            if (!temporaryFile.renameTo(finalFile)) error("atomic publish failed")
            true
        }.getOrDefault(false)
        if (!written) {
            runCatching { temporaryFile.delete() }
            runCatching { finalFile.delete() }
            return Result.Rejected("MMS_PDU_STAGE_FAILED")
        }

        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                finalFile
            )
        }.getOrElse {
            finalFile.delete()
            return Result.Rejected("MMS_SEND_URI_FAILED")
        }

        val cleanupScheduled = runCatching {
            MmsSendCleanupWorker.schedule(context.applicationContext, token)
            true
        }.getOrDefault(false)
        if (!cleanupScheduled) {
            finalFile.delete()
            return Result.Rejected("MMS_PDU_CLEANUP_SCHEDULE_FAILED")
        }

        return Result.Staged(token, finalFile.name, uri)
    }

    fun delete(context: Context, fileName: String): Boolean {
        if (!FILE_NAME.matches(fileName)) return false
        val directory = runCatching { File(context.cacheDir, SEND_DIRECTORY).canonicalFile }.getOrNull()
            ?: return false
        val file = runCatching { File(directory, fileName).canonicalFile }.getOrNull() ?: return false
        if (file.parentFile != directory) return false
        val removed = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
        if (removed) {
            val token = fileName.removeSuffix(".pdu")
            runCatching { MmsSendCleanupWorker.cancel(context.applicationContext, token) }
        }
        return removed
    }

    /**
     * Removes stale/oversized/malformed staged payloads without creating the directory.
     * Called at process start, by durable cleanup work, and before every new stage.
     */
    fun pruneExpired(context: Context): Int {
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return 0
        val directory = File(canonicalCache, SEND_DIRECTORY)
        if (!directory.exists() || !directory.isDirectory) return 0
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return 0
        if (canonicalDirectory.parentFile != canonicalCache) return 0
        return prune(canonicalDirectory)
    }

    private fun prune(directory: File): Int {
        val cutoff = System.currentTimeMillis() - STAGED_PDU_TTL_MS
        var deleted = 0
        directory.listFiles().orEmpty()
            .filter {
                it.isFile && (
                    it.lastModified() < cutoff ||
                    it.length() > MAX_STAGED_PDU_BYTES ||
                    !(FILE_NAME.matches(it.name) || TEMP_NAME.matches(it.name))
                )
            }
            .forEach { if (runCatching { it.delete() }.getOrDefault(false)) deleted++ }
        return deleted
    }

    const val MAX_STAGED_PDU_BYTES = 11L * 1024L * 1024L
    const val STAGED_PDU_TTL_MS = 60L * 60L * 1000L

    private const val SEND_DIRECTORY = "sentinel_mms_send"
    private val FILE_NAME = Regex("^[0-9a-fA-F-]{36}\\.pdu$")
    private val TEMP_NAME = Regex("^[0-9a-fA-F-]{36}\\.tmp$")
}
