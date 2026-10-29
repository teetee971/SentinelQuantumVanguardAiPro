package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/** Real filesystem operations; overridable for deterministic I/O failure qualification. */
internal open class VaultIo {
    open fun writeForced(path: Path, bytes: ByteArray) {
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(channel.write(buffer) > 0)
            channel.force(true)
        }
    }

    open fun forceFile(path: Path) {
        FileChannel.open(path, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
    }

    /** Failure (including unsupported directory fsync) MUST propagate, never imply durability. */
    open fun syncDirectory(path: Path) {
        FileChannel.open(path, READ, NOFOLLOW_LINKS).use { it.force(true) }
    }

    open fun publish(source: Path, destination: Path) {
        val result = RecordingVaultNative.renameNoReplace(source.toString(), destination.toString())
        check(result == 0) { "Atomic publication failed (errno=$result)" }
    }

    open fun delete(path: Path) { Files.deleteIfExists(path) }
}
