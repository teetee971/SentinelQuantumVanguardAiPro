package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption

internal class RecordingVaultStore(
    context: Context,
    directoryName: String = "recording-vault-r2"
) {
    init {
        require(Regex("[A-Za-z0-9_-]{1,64}").matches(directoryName))
    }

    private val root = context.noBackupFilesDir
    private val directory = File(root, directoryName)

    private fun prepareDirectory(): File {
        val canonicalRoot = root.canonicalFile
        check(canonicalRoot.isDirectory)

        if (directory.exists(LinkOption.NOFOLLOW_LINKS)) {
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

    fun save(id: String, plaintext: ByteArray) = synchronized(LOCK) {
        require(plaintext.isNotEmpty())
        require(plaintext.size <= MAX_PLAINTEXT_BYTES)

        val dir = prepareDirectory()
        val destination = target(dir, id)

        check(!destination.exists())
        val ciphertext = RecordingVaultCrypto.encrypt(plaintext)
        val temporary = File.createTempFile("r2-", ".part", dir)

        try {
            FileOutputStream(temporary).use {
                it.write(ciphertext)
                it.fd.sync()
            }

            check(!destination.exists())
            check(temporary.renameTo(destination))
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun read(id: String): ByteArray = synchronized(LOCK) {
        val file = target(prepareDirectory(), id)
        require(file.isFile)
        require(file.length() in 29L..MAX_CIPHERTEXT_BYTES)

        RecordingVaultCrypto.decrypt(file.readBytes())
    }

    fun delete(id: String): Boolean = synchronized(LOCK) {
        val file = target(prepareDirectory(), id)
        !file.exists() || file.isFile && file.delete()
    }

    fun exists(id: String): Boolean = synchronized(LOCK) {
        target(prepareDirectory(), id).isFile
    }

    companion object {
        private val LOCK = Any()
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        private const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
        private const val MAX_CIPHERTEXT_BYTES =
            MAX_PLAINTEXT_BYTES + 29L
    }
}

private fun File.exists(vararg options: LinkOption): Boolean =
    Files.exists(toPath(), *options)
