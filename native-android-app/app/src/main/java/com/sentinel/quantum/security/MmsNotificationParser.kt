package com.sentinel.quantum.security

import java.net.URI

/**
 * Minimal fail-closed parser for an MMS Notification.ind WAP PDU.
 *
 * Carrier Content-Location and X-Mms-Transaction-ID are required for download/correlation. Sender
 * identity is parsed separately and may be unavailable because From is not mandatory for
 * Notification.ind. An unavailable sender may still be downloaded, but must never be used to
 * fabricate a Telephony.Mms thread or address.
 */
object MmsNotificationParser {
    sealed interface SenderIdentity {
        data class Verified(val address: String) : SenderIdentity
        data class Unavailable(val reason: String) : SenderIdentity
    }

    data class Notification(
        val contentLocation: String,
        val transactionId: String,
        val senderIdentity: SenderIdentity
    ) {
        val verifiedSender: String?
            get() = (senderIdentity as? SenderIdentity.Verified)?.address
    }

    sealed interface Inspection {
        data class Accepted(val notification: Notification) : Inspection
        data class Rejected(val reason: String) : Inspection
        data object NotNotification : Inspection
    }

    /** Compatibility helper for self-tests and callers that only need an accepted notification. */
    fun parse(pdu: ByteArray): Notification? =
        (inspect(pdu) as? Inspection.Accepted)?.notification

    /**
     * Distinguishes a non-Notification.ind PDU from a recognized notification whose download
     * correlation is unsafe or incomplete. Sender problems do not block carrier download; they only
     * remove eligibility for provider/thread projection later in the pipeline.
     */
    fun inspect(pdu: ByteArray): Inspection {
        if (pdu.isEmpty()) return Inspection.NotNotification

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
        ) return Inspection.NotNotification

        if (pdu.size > MAX_PDU_BYTES) {
            return Inspection.Rejected("MMS_NOTIFICATION_PDU_TOO_LARGE")
        }

        val locations = linkedSetOf<String>()
        val transactionIds = linkedSetOf<String>()
        val senders = linkedSetOf<String>()
        var locationHeaderSeen = false
        var transactionHeaderSeen = false
        var fromHeaderSeen = false
        var index = messageTypeIndex + 2
        while (index < headerScanLimit) {
            when (u(pdu[index])) {
                CONTENT_LOCATION_HEADER -> {
                    locationHeaderSeen = true
                    val candidate = readTextString(
                        pdu = pdu,
                        startIndex = index + 1,
                        endExclusive = headerScanLimit,
                        maxChars = MAX_LOCATION_CHARS
                    )
                    if (candidate != null && isSafeCarrierLocation(candidate)) locations += candidate
                    if (locations.size > 1) {
                        return Inspection.Rejected("MMS_NOTIFICATION_LOCATION_AMBIGUOUS")
                    }
                }
                TRANSACTION_ID_HEADER -> {
                    transactionHeaderSeen = true
                    val candidate = readTextString(
                        pdu = pdu,
                        startIndex = index + 1,
                        endExclusive = headerScanLimit,
                        maxChars = MAX_TRANSACTION_ID_CHARS
                    )
                    if (candidate != null) transactionIds += candidate
                    if (transactionIds.size > 1) {
                        return Inspection.Rejected("MMS_NOTIFICATION_TRANSACTION_ID_AMBIGUOUS")
                    }
                }
                FROM_HEADER -> {
                    fromHeaderSeen = true
                    val candidate = readFromAddress(pdu, index + 1, headerScanLimit)
                    if (candidate != null) senders += candidate
                }
            }
            index++
        }

        val location = locations.singleOrNull()
            ?: return Inspection.Rejected(
                if (locationHeaderSeen) "MMS_NOTIFICATION_LOCATION_INVALID"
                else "MMS_NOTIFICATION_LOCATION_MISSING"
            )
        val transactionId = transactionIds.singleOrNull()
            ?: return Inspection.Rejected(
                if (transactionHeaderSeen) "MMS_NOTIFICATION_TRANSACTION_ID_INVALID"
                else "MMS_NOTIFICATION_TRANSACTION_ID_MISSING"
            )

        val senderIdentity = when {
            senders.size == 1 -> SenderIdentity.Verified(senders.single())
            senders.size > 1 -> SenderIdentity.Unavailable("MMS_NOTIFICATION_SENDER_AMBIGUOUS")
            fromHeaderSeen -> SenderIdentity.Unavailable("MMS_NOTIFICATION_SENDER_INVALID")
            else -> SenderIdentity.Unavailable("MMS_NOTIFICATION_SENDER_MISSING")
        }
        return Inspection.Accepted(Notification(location, transactionId, senderIdentity))
    }

    private fun readFromAddress(pdu: ByteArray, startIndex: Int, endExclusive: Int): String? {
        val outer = readValueLength(pdu, startIndex, endExclusive) ?: return null
        if (outer.length !in MIN_FROM_VALUE_BYTES..MAX_FROM_VALUE_BYTES) return null
        val valueEnd = outer.valueStart + outer.length
        if (valueEnd > endExclusive) return null

        var index = outer.valueStart
        if (index >= valueEnd || u(pdu[index]) != ADDRESS_PRESENT_TOKEN) return null
        index++

        val rawAddress = readEncodedString(pdu, index, valueEnd) ?: return null
        return normalizeExplicitInternationalSender(rawAddress)
    }

    private fun readEncodedString(pdu: ByteArray, startIndex: Int, endExclusive: Int): String? {
        if (startIndex >= endExclusive) return null
        val first = u(pdu[startIndex])
        if (first > 31) {
            return readTextString(
                pdu,
                startIndex,
                endExclusive,
                MAX_FROM_CHARS,
                requireEnd = true
            )
        }

        val wrapped = readValueLength(pdu, startIndex, endExclusive) ?: return null
        val wrappedEnd = wrapped.valueStart + wrapped.length
        if (wrappedEnd != endExclusive || wrapped.length < 2) return null
        val textStart = skipCharsetInteger(pdu, wrapped.valueStart, wrappedEnd) ?: return null
        return readTextString(
            pdu,
            textStart,
            wrappedEnd,
            MAX_FROM_CHARS,
            requireEnd = true
        )
    }

    private fun skipCharsetInteger(pdu: ByteArray, startIndex: Int, endExclusive: Int): Int? {
        if (startIndex >= endExclusive) return null
        val first = u(pdu[startIndex])
        return when {
            first >= 0x80 -> startIndex + 1
            first in 1..MAX_LONG_INTEGER_BYTES -> {
                val next = startIndex + 1 + first
                next.takeIf { it < endExclusive }
            }
            else -> null
        }
    }

    private data class ValueLength(val length: Int, val valueStart: Int)

    private fun readValueLength(pdu: ByteArray, startIndex: Int, endExclusive: Int): ValueLength? {
        if (startIndex >= endExclusive) return null
        val first = u(pdu[startIndex])
        if (first <= 30) return ValueLength(first, startIndex + 1)
        if (first != 31) return null

        var index = startIndex + 1
        var value = 0L
        repeat(MAX_UINTVAR_BYTES) {
            if (index >= endExclusive) return null
            val next = u(pdu[index++])
            value = (value shl 7) or (next and 0x7f).toLong()
            if (value > Int.MAX_VALUE) return null
            if (next and 0x80 == 0) return ValueLength(value.toInt(), index)
        }
        return null
    }

    private fun readTextString(
        pdu: ByteArray,
        startIndex: Int,
        endExclusive: Int,
        maxChars: Int,
        requireEnd: Boolean = false
    ): String? {
        var index = startIndex
        if (index >= endExclusive) return null
        if (u(pdu[index]) == 0x7f) index++
        val start = index
        var count = 0
        while (index < endExclusive && count <= maxChars) {
            val value = u(pdu[index])
            if (value == 0) {
                if (count == 0) return null
                if (requireEnd && index + 1 != endExclusive) return null
                return pdu.copyOfRange(start, index).toString(Charsets.US_ASCII)
            }
            if (value !in 0x21..0x7e) return null
            index++
            count++
        }
        return null
    }

    private fun normalizeExplicitInternationalSender(value: String): String? {
        val typeIndex = value.indexOf("/TYPE=", ignoreCase = true)
        val address = if (typeIndex >= 0) {
            val suffix = value.substring(typeIndex)
            if (!suffix.equals("/TYPE=PLMN", ignoreCase = true)) return null
            value.substring(0, typeIndex)
        } else {
            value
        }
        if (!(address.startsWith('+') || address.startsWith("00"))) return null
        val normalized = CallRuleEngine.normalizeNumber(address) ?: return null
        return normalized.takeIf { it.startsWith('+') }
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
    private const val FROM_HEADER = 0x89
    private const val TRANSACTION_ID_HEADER = 0x98
    private const val ADDRESS_PRESENT_TOKEN = 0x80
    private const val MAX_PDU_BYTES = 512 * 1024
    private const val MAX_HEADER_SCAN_BYTES = 8192
    private const val MAX_LOCATION_CHARS = 2048
    private const val MAX_TRANSACTION_ID_CHARS = 128
    private const val MAX_FROM_CHARS = 96
    private const val MIN_FROM_VALUE_BYTES = 3
    private const val MAX_FROM_VALUE_BYTES = 160
    private const val MAX_LONG_INTEGER_BYTES = 8
    private const val MAX_UINTVAR_BYTES = 5
}
