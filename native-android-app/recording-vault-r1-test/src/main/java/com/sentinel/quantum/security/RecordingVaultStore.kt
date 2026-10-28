package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

internal class RecordingVaultStore(
    context: Context,
    directoryName: String = "recording-vault-r2"
) {
    init {
        require(Regex("[A-Za-z0-9_-]{1,64}").matches(directoryName))
    }

    private val root = context.noBackupFilesDir
    private val directory = File(root, directoryName)

    // One persistent inode for ALL Store directories sharing the same key alias.
    // Never delete this lock file: replacing it would split cooperating processes.
    // This is coordination, not a security boundary against hostile code with our UID.
    private fun <T> locked(action: () -> T): T = synchronized(LOCK) {
        check(root.isDirectory)
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

        val canonicalDirectory = directory.canonicalFile
        check(canonicalDirectory.parentFile == canonicalRoot)
        check(canonicalDirectory.isDirectory)
        return canonicalDirectory
    }

    private fun target(dir: File, id: String): File {
        require(ID_PATTERN.matches(id))
        val file = File(dir, "$id.rv")
        check(!Files.isSymbolicLink(file.toPath()))
        return file
    }

    fun save(id: String, plaintext: ByteArray) = locked {
        require(plaintext.isNotEmpty())
        require(plaintext.size <= MAX_PLAINTEXT_BYTES)

        val dir = prepareDirectory()
        val destination = target(dir, id).toPath()
        check(!Files.exists(destination, NOFOLLOW_LINKS))
        val ciphertext = RecordingVaultCrypto.encrypt(plaintext)
        val temporary = File(dir, "r2-${UUID.randomUUID()}.part").toPath()

        // Create and open in one operation. Never reopen a pre-created temp pathname.
        FileChannel.open(temporary, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            try {
                val buffer = ByteBuffer.wrap(ciphertext)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)

                // Publish the complete, fsynced temporary file while holding
                // the persistent interprocess lock. All Store writers cooperate.
                // This does not protect against same-UID writers bypassing the lock.
                check(!Files.exists(destination, NOFOLLOW_LINKS)) {
                    "Recording already exists"
                }
                val result = RecordingVaultNative.renameNoReplace(
                    temporary.toString(),
                    destination.toString()
                )
                when (result) {
                    0 -> Unit
                    17 -> error("Recording already exists (EEXIST)")
                    else -> error("Atomic publication failed (errno=$result)")
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    fun read(id: String): ByteArray = locked {
        val file = target(prepareDirectory(), id).toPath()
        require(Files.isRegularFile(file, NOFOLLOW_LINKS))
        FileChannel.open(file, READ, NOFOLLOW_LINKS).use { channel ->
            // Bound allocation and reads using the opened inode, not pathname length
            // followed by unbounded File.readBytes(). Reject growth/truncation.
            val size = channel.size()
            require(size in 30L..MAX_CIPHERTEXT_BYTES)
            val bytes = ByteArray(size.toInt())
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                check(channel.read(buffer) > 0) { "Recording truncated during read" }
            }
            check(channel.read(ByteBuffer.allocate(1)) == -1) { "Recording grew during read" }
            RecordingVaultCrypto.decrypt(bytes)
        }
    }

    fun delete(id: String): Boolean = locked {
        val file = target(prepareDirectory(), id)
        !Files.exists(file.toPath(), NOFOLLOW_LINKS) ||
            Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.delete()
    }

    fun exists(id: String): Boolean = locked {
        Files.isRegularFile(target(prepareDirectory(), id).toPath(), NOFOLLOW_LINKS)
    }

    companion object {
        private val LOCK = Any()
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
        private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 29L
    }
}
