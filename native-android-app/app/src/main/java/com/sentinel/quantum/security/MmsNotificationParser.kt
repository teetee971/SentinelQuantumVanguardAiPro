package com.sentinel.quantum.security

import java.net.URI

/**
 * Minimal fail-closed parser for an MMS Notification.ind WAP PDU.
 *
 * It extracts only the carrier Content-Location needed by Android's public MMS download API.
 * No sender, subject or arbitrary header is exposed to the rest of the application.
 */
object MmsNotificationParser {
    data class Notification(val contentLocation: String)

    fun parse(pdu: ByteArray): Notification? {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return null

        val headerScanLimit = minOf(pdu.size, MAX_HEADER_SCAN_BYTES)
        var messageTypeIndex = -1
        for (i in 0 until (headerScanLimit - 1).coerceAtLeast(0)) {
            if (u(pdu[i]) == MESSAGE_TYPE_HEADER) {
                messageTypeIndex = i
                break
            }
        }
        if (
            messageTypeIndex < 0 ||
            messageTypeIndex + 1 >= headerScanLimit ||
            u(pdu[messageTypeIndex + 1]) != MESSAGE_TYPE_NOTIFICATION_IND
        ) return null

        val locations = linkedSetOf<String>()
        for (i in (messageTypeIndex + 2) until (headerScanLimit - 1).coerceAtLeast(messageTypeIndex + 2)) {
            if (u(pdu[i]) != CONTENT_LOCATION_HEADER) continue
            val candidate = readTextString(pdu, i + 1) ?: continue
            if (isSafeCarrierLocation(candidate)) locations += candidate
            if (locations.size > 1) return null
        }

        return locations.singleOrNull()?.let(::Notification)
    }

    private fun readTextString(pdu: ByteArray, startIndex: Int): String? {
        var index = startIndex
        if (index >= pdu.size) return null
        if (u(pdu[index]) == 0x7f) index++
        val start = index
        var count = 0
        while (index < pdu.size && count <= MAX_LOCATION_CHARS) {
            val value = u(pdu[index])
            if (value == 0) {
                if (count == 0) return null
                return pdu.copyOfRange(start, index).toString(Charsets.US_ASCII)
            }
            if (value !in 0x21..0x7e) return null
            index++
            count++
        }
        return null
    }

    private fun isSafeCarrierLocation(value: String): Boolean {
        if (value.length !in 8..MAX_LOCATION_CHARS) return false
        if (value.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return false
        return true
    }

    private fun u(value: Byte): Int = value.toInt() and 0xff

    private const val MESSAGE_TYPE_HEADER = 0x8c
    private const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
    private const val CONTENT_LOCATION_HEADER = 0x83
    private const val MAX_PDU_BYTES = 512 * 1024
    private const val MAX_HEADER_SCAN_BYTES = 8192
    private const val MAX_LOCATION_CHARS = 2048
}
