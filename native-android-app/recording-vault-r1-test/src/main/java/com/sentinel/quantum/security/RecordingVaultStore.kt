package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/**
 * V1 stays at id.rv. Migration publishes id.v2.rv without replacing the source.
 * id.v2.required is an authenticated INTENT, not a claim of completed durability.
 * Once present it forbids fallback to V1. See the migration design for crash states.
 * All filesystem errors propagate; a failed call may already have published files.
 */
internal class RecordingVaultStore(
    context: Context,
    private val directoryName: String = "recording-vault-r2",
    private val io: VaultIo = VaultIo()
) {
    init { require(ID_PATTERN.matches(directoryName)) }
    private val root = context.noBackupFilesDir
    private val directory = File(root, directoryName)

    // Persistent inode, shared by all cooperating stores/processes. Never delete it.
    private fun <T> locked(action: () -> T): T = synchronized(LOCK) {
        check(!Files.isSymbolicLink(root.toPath()) && root.isDirectory)
        val lockPath = File(root, ".recording-vault-r3.lock").toPath()
        check(!Files.isSymbolicLink(lockPath))
        FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.lock().use { action() }
        }
    }

    private fun prepareDirectory(): File {
        val canonicalRoot = root.canonicalFile
        check(canonicalRoot.isDirectory)
        if (Files.exists(directory.toPath(), NOFOLLOW_LINKS)) {
            check(!Files.isSymbolicLink(directory.toPath()))
        } else {
            check(directory.mkdir())
        }
        val dir = directory.canonicalFile
        check(dir.parentFile == canonicalRoot && dir.isDirectory)
        // Also persists creation of the vault directory before any acknowledged write.
        io.syncDirectory(canonicalRoot.toPath())
        return dir
    }

    private fun path(dir: File, id: String, suffix: String): Path {
        require(ID_PATTERN.matches(id))
        val path = File(dir, "$id$suffix").toPath()
        check(!Files.isSymbolicLink(path))
        if (present(path)) check(Files.isRegularFile(path, NOFOLLOW_LINKS))
        return path
    }

    private fun present(path: Path) = Files.exists(path, NOFOLLOW_LINKS)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun readBytes(path: Path, maximum: Long = MAX_CIPHERTEXT_BYTES): ByteArray {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS))
        FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            require(size in 30L..maximum)
            val bytes = ByteArray(size.toInt())
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(channel.read(buffer) > 0) { "Recording truncated" }
            check(channel.read(ByteBuffer.allocate(1)) == -1) { "Recording grew during read" }
            return bytes
        }
    }

    private data class Intent(val mode: Int, val sourceHash: ByteArray, val finalHash: ByteArray) {
        fun bytes() = byteArrayOf(mode.toByte()) + sourceHash + finalHash
    }

    private fun readIntent(dir: File, id: String): Intent {
        val bytes = readBytes(path(dir, id, REQUIRED), 94)
        require(bytes.size == 94)
        val plain = RecordingVaultCrypto.decryptCommit(bytes, directoryName, id)
        require(plain.size == 65 && plain[0].toInt() in 1..2)
        val intent = Intent(plain[0].toInt(), plain.copyOfRange(1, 33), plain.copyOfRange(33, 65))
        require(intent.mode == 1 || intent.sourceHash.all { it == 0.toByte() })
        return intent
    }

    private fun commitIntent(dir: File, id: String, intent: Intent) {
        val pending = path(dir, id, REQUIRED_PENDING)
        // Non-authoritative staging only. Never remove a committed marker.
        io.delete(pending)
        val bytes = RecordingVaultCrypto.encryptCommit(intent.bytes(), directoryName, id)
        io.writeForced(pending, bytes)
        check(RecordingVaultCrypto.decryptCommit(readBytes(pending, 94), directoryName, id)
            .contentEquals(intent.bytes()))
        io.publish(pending, path(dir, id, REQUIRED))
        io.syncDirectory(dir.toPath())
    }

    private fun validateV2(path: Path, id: String, intent: Intent): ByteArray {
        val bytes = readBytes(path)
        // Authenticate first: wrong context/tag is never interpreted as another format.
        val plain = RecordingVaultCrypto.decryptV2(bytes, directoryName, id)
        check(MessageDigest.isEqual(digest(bytes), intent.finalHash)) { "V2 differs from committed intent" }
        return plain
    }

    private fun finish(dir: File, id: String, intent: Intent): ByteArray {
        val required = path(dir, id, REQUIRED)
        val pending = path(dir, id, PENDING)
        val destination = path(dir, id, if (intent.mode == 1) V2 else ORIGINAL)
        // Re-establish durability of intent before attempting final publication on retry.
        io.forceFile(required)
        io.syncDirectory(dir.toPath())
        if (!present(destination)) {
            validateV2(pending, id, intent) // Missing/corrupt pending => fail, never read V1.
            io.forceFile(pending)
            io.publish(pending, destination)
            io.syncDirectory(dir.toPath())
        } else {
            check(!present(pending)) { "Conflicting pending and committed recording" }
        }
        val plain = validateV2(destination, id, intent)
        io.forceFile(destination)
        io.forceFile(required)
        io.syncDirectory(dir.toPath())
        return plain
    }

    private fun stage(dir: File, id: String, plaintext: ByteArray): ByteArray {
        val pending = path(dir, id, PENDING)
        val bytes = RecordingVaultCrypto.encryptV2(plaintext, directoryName, id)
        io.writeForced(pending, bytes)
        io.syncDirectory(dir.toPath())
        val reread = readBytes(pending)
        check(RecordingVaultCrypto.decryptV2(reread, directoryName, id).contentEquals(plaintext))
        check(reread.contentEquals(bytes))
        return bytes
    }

    fun save(id: String, plaintext: ByteArray) = locked {
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES)
        val dir = prepareDirectory()
        for (suffix in listOf(ORIGINAL, V2, REQUIRED, PENDING, REQUIRED_PENDING)) {
            check(!present(path(dir, id, suffix))) { "Recording or recovery artifact already exists" }
        }
        val bytes = stage(dir, id, plaintext)
        commitIntent(dir, id, Intent(2, ByteArray(32), digest(bytes)))
        finish(dir, id, readIntent(dir, id))
        Unit
    }

    /** Explicit migration; V1 is never removed. Repeated calls do not rewrite committed V2. */
    fun migrate(id: String): Boolean = locked {
        val dir = prepareDirectory()
        if (present(path(dir, id, REQUIRED))) {
            val intent = readIntent(dir, id)
            val resuming = !present(path(dir, id, if (intent.mode == 1) V2 else ORIGINAL))
            finish(dir, id, intent)
            return@locked resuming
        }
        check(!present(path(dir, id, V2))) { "V2 marker missing; recovery required" }
        if (!present(path(dir, id, ORIGINAL)) && present(path(dir, id, PENDING))) {
            recoverUncommittedSave(dir, id)
            return@locked true
        }
        val source = readBytes(path(dir, id, ORIGINAL))
        if (source[0] == 2.toByte()) {
            enroll(dir, id, source)
            return@locked false
        }
        val plaintext = RecordingVaultCrypto.decrypt(source) // Complete authentication before mutation.
        io.forceFile(path(dir, id, ORIGINAL))
        io.syncDirectory(dir.toPath())
        // Only discard uncommitted preparations after authenticating the recoverable V1.
        io.delete(path(dir, id, PENDING))
        io.delete(path(dir, id, REQUIRED_PENDING))
        val bytes = stage(dir, id, plaintext)
        commitIntent(dir, id, Intent(1, digest(source), digest(bytes)))
        finish(dir, id, readIntent(dir, id))
        true
    }

    private fun enroll(dir: File, id: String, bytes: ByteArray): ByteArray {
        RecordingVaultCrypto.decryptV2(bytes, directoryName, id)
        check(!present(path(dir, id, PENDING))) { "Uncommitted recording artifact" }
        io.forceFile(path(dir, id, ORIGINAL))
        io.syncDirectory(dir.toPath())
        commitIntent(dir, id, Intent(2, ByteArray(32), digest(bytes)))
        return finish(dir, id, readIntent(dir, id))
    }

    /** Recover the sole prepared new-save copy; never use it over an existing source. */
    private fun recoverUncommittedSave(dir: File, id: String): ByteArray {
        check(!present(path(dir, id, ORIGINAL)) && !present(path(dir, id, V2)) &&
            !present(path(dir, id, REQUIRED)))
        val pending = path(dir, id, PENDING)
        val bytes = readBytes(pending)
        RecordingVaultCrypto.decryptV2(bytes, directoryName, id)
        io.forceFile(pending)
        io.syncDirectory(dir.toPath())
        commitIntent(dir, id, Intent(2, ByteArray(32), digest(bytes)))
        return finish(dir, id, readIntent(dir, id))
    }

    fun read(id: String): ByteArray = locked {
        val dir = prepareDirectory()
        if (present(path(dir, id, REQUIRED))) return@locked finish(dir, id, readIntent(dir, id))
        check(!present(path(dir, id, V2))) { "V2 marker missing; refusing V1 fallback" }
        if (!present(path(dir, id, ORIGINAL)) && present(path(dir, id, PENDING))) {
            return@locked recoverUncommittedSave(dir, id)
        }
        val bytes = readBytes(path(dir, id, ORIGINAL))
        when (bytes[0].toInt()) {
            1 -> RecordingVaultCrypto.decrypt(bytes)
            2 -> enroll(dir, id, bytes)
            else -> throw IllegalArgumentException("Unsupported recording version")
        }
    }

    /** Explicit caller deletion only; remove the anti-downgrade marker LAST. */
    fun delete(id: String): Boolean = locked {
        val dir = prepareDirectory()
        val files = listOf(ORIGINAL, PENDING, V2, REQUIRED_PENDING, REQUIRED).map { path(dir, id, it) }
        files.dropLast(1).forEach { io.delete(it) }
        io.syncDirectory(dir.toPath())
        io.delete(files.last())
        io.syncDirectory(dir.toPath())
        true
    }

    fun exists(id: String): Boolean = locked {
        val dir = prepareDirectory()
        listOf(ORIGINAL, V2, REQUIRED, PENDING).map { path(dir, id, it) }.any { present(it) }
    }

    companion object {
        private val LOCK = Any()
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
        private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 29L
        private const val ORIGINAL = ".rv"
        private const val V2 = ".v2.rv"
        private const val PENDING = ".v2.pending"
        private const val REQUIRED = ".v2.required"
        private const val REQUIRED_PENDING = ".v2.required.pending"
    }
}
