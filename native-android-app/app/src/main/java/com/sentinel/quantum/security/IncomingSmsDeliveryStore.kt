package com.sentinel.quantum.security

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Small app-private durable inbox for SMS_DELIVER.
 *
 * The Android broadcast is considered captured only after this store has fsync'd a bounded record
 * or the receiver has synchronously projected the same record into the system SMS provider. Raw
 * PDUs are used only to derive the idempotency key and are never persisted here.
 */
internal object IncomingSmsDeliveryStore {
    data class Record(
        val id: String,
        val address: String,
        val body: String,
        val receivedAtMs: Long,
        val sentAtMs: Long?,
        val subscriptionId: Int?
    )

    enum class PersistState {
        CREATED,
        EXISTING,
        CAPACITY_EXCEEDED,
        FAILED
    }

    @Synchronized
    fun persist(
        filesDir: File,
        record: Record,
        maxPendingRecords: Int = MAX_PENDING_RECORDS
    ): PersistState {
        if (!isValid(record) || maxPendingRecords <= 0) return PersistState.FAILED
        val directory = directory(filesDir) ?: return PersistState.FAILED
        cleanTemporaryFiles(directory)
        cleanInvalidRecordFiles(directory)
        val target = recordFile(directory, record.id) ?: return PersistState.FAILED
        if (target.isFile) {
            if (decodeRecordFile(target, record.id) != null) return PersistState.EXISTING
            if (!runCatching { target.delete() }.getOrDefault(false)) return PersistState.FAILED
        }

        val pending = directory.listFiles().orEmpty().count { it.isFile && RECORD_FILE.matches(it.name) }
        if (pending >= maxPendingRecords) return PersistState.CAPACITY_EXCEEDED

        val temporary = File(directory, ".${record.id}.${System.nanoTime()}.tmp")
        val written = runCatching {
            FileOutputStream(temporary).use { stream ->
                DataOutputStream(BufferedOutputStream(stream)).use { output ->
                    output.writeInt(FORMAT_VERSION)
                    output.writeUTF(record.id)
                    output.writeUTF(record.address)
                    output.writeUTF(record.body)
                    output.writeLong(record.receivedAtMs)
                    output.writeBoolean(record.sentAtMs != null)
                    record.sentAtMs?.let(output::writeLong)
                    output.writeBoolean(record.subscriptionId != null)
                    record.subscriptionId?.let(output::writeInt)
                    output.flush()
                    stream.fd.sync()
                }
            }
            if (target.exists()) {
                temporary.delete()
                true
            } else {
                temporary.renameTo(target)
            }
        }.getOrDefault(false)

        if (!written) {
            runCatching { temporary.delete() }
            return PersistState.FAILED
        }
        return if (target.isFile && decodeRecordFile(target, record.id) == record) {
            PersistState.CREATED
        } else {
            runCatching { target.delete() }
            PersistState.FAILED
        }
    }

    @Synchronized
    fun read(filesDir: File, id: String): Record? {
        if (!ID.matches(id)) return null
        val directory = directory(filesDir, create = false) ?: return null
        val target = recordFile(directory, id) ?: return null
        if (!target.isFile) return null
        val decoded = decodeRecordFile(target, id)
        if (decoded == null) runCatching { target.delete() }
        return decoded
    }

    /** True when a staged file still owns this identity, including an unreadable file. */
    @Synchronized
    fun hasPending(filesDir: File, id: String): Boolean {
        if (!ID.matches(id)) return false
        val directory = directory(filesDir, create = false) ?: return false
        val target = recordFile(directory, id) ?: return false
        return target.isFile
    }

    @Synchronized
    fun delete(filesDir: File, id: String): Boolean {
        if (!ID.matches(id)) return false
        val directory = directory(filesDir, create = false) ?: return true
        val target = recordFile(directory, id) ?: return false
        return !target.exists() || runCatching { target.delete() }.getOrDefault(false)
    }

    @Synchronized
    fun pendingIds(filesDir: File): List<String> {
        val directory = directory(filesDir, create = false) ?: return emptyList()
        cleanTemporaryFiles(directory)
        cleanInvalidRecordFiles(directory)
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && RECORD_FILE.matches(it.name) && it.length() in 1..MAX_RECORD_BYTES }
            .map { it.name.removeSuffix(RECORD_SUFFIX) }
            .filter { ID.matches(it) }
            .take(MAX_PENDING_RECORDS)
            .toList()
    }

    fun identity(
        pdus: List<ByteArray>,
        address: String,
        body: String,
        sentAtMs: Long?,
        subscriptionId: Int?
    ): String? {
        if (address.isBlank() || body.isBlank()) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            updateBytes(digest, DOMAIN.toByteArray(Charsets.UTF_8))
            updateInt(digest, subscriptionId ?: -1)
            updateLong(digest, sentAtMs ?: -1L)
            if (pdus.isNotEmpty()) {
                updateInt(digest, pdus.size)
                pdus.forEach { pdu -> updateBytes(digest, pdu) }
            } else {
                updateBytes(digest, address.toByteArray(Charsets.UTF_8))
                updateBytes(digest, body.toByteArray(Charsets.UTF_8))
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun decodeRecordFile(target: File, expectedId: String): Record? {
        if (!target.isFile || target.length() !in 1..MAX_RECORD_BYTES || !ID.matches(expectedId)) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(target))).use { input ->
                if (input.readInt() != FORMAT_VERSION) return@use null
                val record = Record(
                    id = input.readUTF(),
                    address = input.readUTF(),
                    body = input.readUTF(),
                    receivedAtMs = input.readLong(),
                    sentAtMs = if (input.readBoolean()) input.readLong() else null,
                    subscriptionId = if (input.readBoolean()) input.readInt() else null
                )
                record.takeIf { it.id == expectedId && isValid(it) }
            }
        }.getOrNull()
    }

    private fun isValid(record: Record): Boolean =
        ID.matches(record.id) &&
            record.address.isNotBlank() &&
            record.address.length <= MAX_ADDRESS_CHARS &&
            record.body.isNotBlank() &&
            record.body.length <= SentinelSmsSender.MAX_BODY_CHARS &&
            record.receivedAtMs > 0L &&
            (record.sentAtMs == null || record.sentAtMs > 0L) &&
            (record.subscriptionId == null || record.subscriptionId >= 0)

    private fun directory(filesDir: File, create: Boolean = true): File? {
        val canonicalRoot = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val directory = File(canonicalRoot, DIRECTORY_NAME)
        if (create && !directory.exists() && !directory.mkdirs()) return null
        if (!directory.exists() || !directory.isDirectory) return null
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (canonicalDirectory.parentFile != canonicalRoot) return null
        return canonicalDirectory
    }

    private fun recordFile(directory: File, id: String): File? {
        if (!ID.matches(id)) return null
        val target = runCatching { File(directory, id + RECORD_SUFFIX).canonicalFile }.getOrNull() ?: return null
        if (target.parentFile != directory) return null
        return target
    }

    private fun cleanTemporaryFiles(directory: File) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(".") && it.name.endsWith(".tmp") }
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun cleanInvalidRecordFiles(directory: File) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && RECORD_FILE.matches(it.name) }
            .forEach { file ->
                val id = file.name.removeSuffix(RECORD_SUFFIX)
                if (decodeRecordFile(file, id) == null) runCatching { file.delete() }
            }
    }

    private fun updateBytes(digest: MessageDigest, bytes: ByteArray) {
        updateInt(digest, bytes.size)
        digest.update(bytes)
    }

    private fun updateInt(digest: MessageDigest, value: Int) {
        digest.update(byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        ))
    }

    private fun updateLong(digest: MessageDigest, value: Long) {
        for (shift in 56 downTo 0 step 8) digest.update((value ushr shift).toByte())
    }

    internal const val MAX_PENDING_RECORDS = 512
    internal const val MAX_RECORD_BYTES = 64L * 1024L
    internal const val MAX_ADDRESS_CHARS = 128
    private const val DIRECTORY_NAME = "sentinel_sms_inbound"
    private const val RECORD_SUFFIX = ".sms"
    private const val FORMAT_VERSION = 1
    private const val DOMAIN = "sentinel.sms.inbound.v1"
    private val ID = Regex("^[0-9a-f]{64}$")
    private val RECORD_FILE = Regex("^[0-9a-f]{64}\\.sms$")
}
