package com.sentinel.quantum.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Atomic same-directory replacement; app production uses noBackupFilesDir, never shared preferences. */
internal class ContactSocialProfileFile(
    private val directory: File,
    private val replace: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
) {
    fun read(): String? {
        val root = directory.canonicalFile
        val file = File(root, NAME)
        if (!file.exists()) return null
        if (file.canonicalFile.parentFile != root || !file.isFile || file.length() > MAX_BYTES) throw IOException("Invalid profile storage")
        val data = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (data.size() + count > MAX_BYTES) throw IOException("Profile storage too large")
                data.write(buffer, 0, count)
            }
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data.toByteArray())).toString()
    }

    fun write(raw: String): Boolean {
        if (raw.length > MAX_BYTES) return false
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return false
        var temporary: File? = null
        return try {
            val root = directory.canonicalFile
            if (!root.isDirectory && !root.mkdirs()) return false
            val target = File(root, NAME)
            if (target.canonicalFile.parentFile != root || (target.exists() && !target.isFile)) return false
            val pending = File.createTempFile("social-profiles-", ".tmp", root)
            temporary = pending
            FileOutputStream(pending).use { output -> output.write(bytes); output.fd.sync() }
            replace(pending, target)
        } catch (_: Exception) { false }
        finally { temporary?.delete() }
    }

    companion object {
        private const val NAME = "sentinel_contact_profiles_v1.json"
        private const val MAX_BYTES = 1024 * 1024
    }
}
