package com.sentinel.quantum.security

import java.security.MessageDigest

/** Stable, content-derived identity for one downloaded incoming MMS PDU. */
internal object IncomingMmsIdentity {
    private val DIGEST = Regex("^[0-9a-f]{64}$")

    fun sha256Hex(data: ByteArray): String? {
        if (data.isEmpty() || data.size.toLong() > MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            return null
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun persistedFileName(digestHex: String): String? =
        digestHex.lowercase()
            .takeIf(DIGEST::matches)
            ?.let { "$it.pdu" }

    fun partialFileName(digestHex: String): String? =
        digestHex.lowercase()
            .takeIf(DIGEST::matches)
            ?.let { "$it.part" }
}
