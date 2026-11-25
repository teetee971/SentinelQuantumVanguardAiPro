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
    fun persist(
        filesDir: File,
        data: ByteArray,
        pinnedDigests: Set<String> = emptySet(),
        retainOnCommit: Boolean = false,
        subscriptionId: Int? = null
    ): Result {
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
            val matches = target.isFile &&
                target.length() == data.size.toLong() &&
                digestFile(target) == digest
            if (!matches) return failed()
            if (
                retainOnCommit &&
                !IncomingMmsPrivateRetention.retain(canonicalRoot, digest, subscriptionId)
            ) {
                retainUntilJournaled(digest)
            }
            return Result(State.EXISTING, digest)
        }

        // With the process-wide monitor held, any .part is crash residue rather than a live writer.
        val residues = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == PART_EXTENSION }
            .orEmpty()
        for (residue in residues) {
            if (!runCatching { residue.delete() }.getOrDefault(false)) return failed()
        }

        if (partial.exists() && !runCatching { partial.delete() }.getOrDefault(false)) return failed()

        val written = try {
            FileOutputStream(partial).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            if (!partial.renameTo(target)) {
                val matchingTarget = target.isFile &&
                    target.length() == data.size.toLong() &&
                    digestFile(target) == digest
                runCatching { partial.delete() }
                matchingTarget
            } else {
                true
            }
        } catch (_: Exception) {
            runCatching { partial.delete() }
            false
        }
        if (!written || digestFile(target) != digest) {
            runCatching { partial.delete() }
            return failed()
        }

        // Evict only after the new bytes are durable.
        // Journal pins and the in-memory hold may exceed the cap: dropping them would drop
        // the only in-flight copy. A private message already in the retention ledger is
        // never a victim. The new file is kept only when a slot remains, unless it is an
        // in-flight pin. Otherwise it is removed and the call fails closed.
        val mustKeep = pinnedDigests.mapNotNull(IncomingMmsIdentity::persistedFileName).toSet() +
            retainedUntilJournal.mapNotNull(IncomingMmsIdentity::persistedFileName).toSet()
        val retained = IncomingMmsPrivateRetention.read(canonicalRoot)
            .mapNotNull { IncomingMmsIdentity.persistedFileName(it.digest) }
            .toSet()
        val protected = mustKeep + retained
        val targetIsMustKeep = target.name in mustKeep || targetName in mustKeep
        var stored = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == PDU_EXTENSION }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
            .orEmpty()
        while (stored.size > MAX_STORED_MMS) {
            val victim = stored.lastOrNull { it.name !in protected && it.name != target.name } ?: break
            if (!runCatching { victim.delete() }.getOrDefault(false)) return failed()
            stored = stored.filterNot { it == victim }
        }
        if (stored.size > MAX_STORED_MMS && !targetIsMustKeep) {
            if (!runCatching { target.delete() }.getOrDefault(false)) return failed()
            return failed()
        }
        if (retainOnCommit && !IncomingMmsPrivateRetention.retain(canonicalRoot, digest, subscriptionId)) {
            retainUntilJournaled(digest)
        }
        return Result(State.CREATED, digest)
    }

    /**
     * Persist, then prove the private file still matches. Does not touch the journal.
     * The digest stays retained until [releaseJournaled] so a later persist cannot evict it first.
     */
    fun confirmDurableCopy(
        filesDir: File,
        data: ByteArray,
        pinnedDigests: Set<String> = emptySet(),
        subscriptionId: Int? = null
    ): Result? {
        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return null
        retainUntilJournaled(digest)
        val persisted = persist(
            filesDir,
            data,
            pinnedDigests + digest,
            retainOnCommit = true,
            subscriptionId = subscriptionId
        )
        if (
            (persisted.state != State.CREATED && persisted.state != State.EXISTING) ||
            persisted.digestHex != digest
        ) {
            releaseJournaled(digest)
            return null
        }
        val again = read(filesDir, digest)
        if (again == null || IncomingMmsIdentity.sha256Hex(again) != digest) {
            releaseJournaled(digest)
            return null
        }
        return persisted
    }

    /** Drops both the process pin and the durable ledger once a provider row is the copy of record. */
    @Synchronized
    fun releaseProjected(filesDir: File, digestHex: String): Boolean {
        releaseJournaled(digestHex)
        return IncomingMmsPrivateRetention.release(filesDir, digestHex)
    }

    /** Keeps a digest that is about to be journaled from being evicted by another persist. */
    fun retainUntilJournaled(digestHex: String) {
        val normalized = digestHex.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalized) != null) {
            retainedUntilJournal.add(normalized)
        }
    }

    fun releaseJournaled(digestHex: String) {
        retainedUntilJournal.remove(digestHex.lowercase())
    }

    /** Reads a private PDU only when its file name and contents still match the digest. */
    fun read(filesDir: File, digestHex: String): ByteArray? {
        val name = IncomingMmsIdentity.persistedFileName(digestHex) ?: return null
        val canonicalRoot = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val directory = runCatching { File(canonicalRoot, DIRECTORY_NAME).canonicalFile }.getOrNull()
            ?: return null
        if (directory.parentFile != canonicalRoot || !directory.isDirectory) return null
        val target = runCatching { File(directory, name).canonicalFile }.getOrNull() ?: return null
        if (target.parentFile != directory || !target.isFile) return null
        val data = IncomingMmsPduFileReader.read(target, target.length()) ?: return null
        return data.takeIf { IncomingMmsIdentity.sha256Hex(it) == name.removeSuffix(".$PDU_EXTENSION") }
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
    private val retainedUntilJournal = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
}
