package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsNotificationParserTest {
    @Test fun parsesSafeCarrierLocationTransactionAndExplicitInternationalSender() {
        val location = "http://mmsc.example.test/mms/123"
        val sender = "+33612345678"
        val transactionId = "notif-123"
        val notification = MmsNotificationParser.parse(
            notificationPdu(location, sender, transactionId = transactionId)
        )

        assertEquals(location, notification?.contentLocation)
        assertEquals(transactionId, notification?.transactionId)
        assertEquals(sender, notification?.verifiedSender)
        assertTrue(
            MmsNotificationParser.inspect(
                notificationPdu(location, sender, transactionId = transactionId)
            ) is MmsNotificationParser.Inspection.Accepted
        )
    }

    @Test fun acceptsEncodedStringCharsetAndNormalizesPlmnSuffix() {
        val location = "https://mmsc.example.test/mms/456"
        val sender = "+590690123456"
        val notification = MmsNotificationParser.parse(
            notificationPdu(location, sender, wrappedUtf8 = true)
        )

        assertEquals(location, notification?.contentLocation)
        assertEquals(DEFAULT_TRANSACTION_ID, notification?.transactionId)
        assertEquals(sender, notification?.verifiedSender)
    }

    @Test fun classifiesOtherMessageTypesAsNotNotification() {
        val valid = notificationPdu("http://mmsc.example.test/mms/123", "+33612345678")
        val pdu = valid.copyOf().also { it[1] = 0x84.toByte() }

        assertNull(MmsNotificationParser.parse(pdu))
        assertTrue(
            MmsNotificationParser.inspect(pdu) ===
                MmsNotificationParser.Inspection.NotNotification
        )
    }

    @Test fun missingTransactionIdRejectsRecognizedNotification() {
        val location = "http://mmsc.example.test/mms/123"
        val pdu = byteArrayOf(0x8c.toByte(), 0x82.toByte()) +
            fromHeader("+33612345678") + byteArrayOf(0x83.toByte()) +
            location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        val result = MmsNotificationParser.inspect(pdu)
        assertTrue(result is MmsNotificationParser.Inspection.Rejected)
        assertEquals(
            "MMS_NOTIFICATION_TRANSACTION_ID_MISSING",
            (result as MmsNotificationParser.Inspection.Rejected).reason
        )
    }

    @Test fun missingSenderDoesNotBlockDownloadButDisablesProviderIdentity() {
        val location = "http://mmsc.example.test/mms/123"
        val pdu = byteArrayOf(
            0x8c.toByte(),
            0x82.toByte(),
            0x98.toByte()
        ) + DEFAULT_TRANSACTION_ID.toByteArray(Charsets.US_ASCII) + byteArrayOf(
            0,
            0x83.toByte()
        ) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        val result = MmsNotificationParser.inspect(pdu)
        assertTrue(result is MmsNotificationParser.Inspection.Accepted)
        val notification = (result as MmsNotificationParser.Inspection.Accepted).notification
        assertEquals(location, notification.contentLocation)
        assertEquals(DEFAULT_TRANSACTION_ID, notification.transactionId)
        assertNull(notification.verifiedSender)
        assertEquals(
            "MMS_NOTIFICATION_SENDER_MISSING",
            (notification.senderIdentity as MmsNotificationParser.SenderIdentity.Unavailable).reason
        )
    }

    @Test fun nationalSenderIsNotUsedToInventCountryContext() {
        val result = MmsNotificationParser.inspect(
            notificationPdu("http://mmsc.example.test/mms/123", "0612345678")
        )
        assertTrue(result is MmsNotificationParser.Inspection.Accepted)
        val notification = (result as MmsNotificationParser.Inspection.Accepted).notification
        assertNull(notification.verifiedSender)
        assertEquals(
            "MMS_NOTIFICATION_SENDER_INVALID",
            (notification.senderIdentity as MmsNotificationParser.SenderIdentity.Unavailable).reason
        )
    }

    @Test fun rejectsUnsafeOrAmbiguousLocations() {
        val unsafe = MmsNotificationParser.inspect(
            notificationPdu("file:///tmp/mms", "+33612345678")
        )
        assertTrue(unsafe is MmsNotificationParser.Inspection.Rejected)
        assertEquals(
            "MMS_NOTIFICATION_LOCATION_INVALID",
            (unsafe as MmsNotificationParser.Inspection.Rejected).reason
        )

        val first = "http://mmsc.example.test/a"
        val second = "http://mmsc.example.test/b"
        val prefix = notificationPrefix("+33612345678")
        val ambiguousPdu = prefix + byteArrayOf(0x83.toByte()) +
            first.toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0x83.toByte()) +
            second.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val ambiguous = MmsNotificationParser.inspect(ambiguousPdu)
        assertTrue(ambiguous is MmsNotificationParser.Inspection.Rejected)
        assertEquals(
            "MMS_NOTIFICATION_LOCATION_AMBIGUOUS",
            (ambiguous as MmsNotificationParser.Inspection.Rejected).reason
        )
    }

    @Test fun ambiguousTransactionIdIsRejected() {
        val location = "http://mmsc.example.test/mms/123"
        val pdu = notificationPrefix("+33612345678", transactionId = "txn-a") +
            byteArrayOf(0x98.toByte()) + "txn-b".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
            byteArrayOf(0x83.toByte()) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val result = MmsNotificationParser.inspect(pdu)
        assertTrue(result is MmsNotificationParser.Inspection.Rejected)
        assertEquals(
            "MMS_NOTIFICATION_TRANSACTION_ID_AMBIGUOUS",
            (result as MmsNotificationParser.Inspection.Rejected).reason
        )
    }

    @Test fun ambiguousOrUnsupportedSenderDisablesProviderIdentityWithoutBlockingDownload() {
        val location = "http://mmsc.example.test/mms/123"
        val first = notificationPrefix("+33612345678")
        val secondFrom = fromHeader("+33687654321")
        val ambiguousPdu = first + secondFrom + byteArrayOf(0x83.toByte()) +
            location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val ambiguous = MmsNotificationParser.inspect(ambiguousPdu)
        assertTrue(ambiguous is MmsNotificationParser.Inspection.Accepted)
        val ambiguousNotification =
            (ambiguous as MmsNotificationParser.Inspection.Accepted).notification
        assertNull(ambiguousNotification.verifiedSender)
        assertEquals(
            "MMS_NOTIFICATION_SENDER_AMBIGUOUS",
            (ambiguousNotification.senderIdentity as MmsNotificationParser.SenderIdentity.Unavailable).reason
        )

        val insertAddress = byteArrayOf(
            0x8c.toByte(), 0x82.toByte(),
            0x98.toByte()
        ) + DEFAULT_TRANSACTION_ID.toByteArray(Charsets.US_ASCII) + byteArrayOf(
            0,
            0x89.toByte(), 0x01, 0x81.toByte(),
            0x83.toByte()
        ) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val unsupported = MmsNotificationParser.inspect(insertAddress)
        assertTrue(unsupported is MmsNotificationParser.Inspection.Accepted)
        val unsupportedNotification =
            (unsupported as MmsNotificationParser.Inspection.Accepted).notification
        assertNull(unsupportedNotification.verifiedSender)
        assertEquals(
            "MMS_NOTIFICATION_SENDER_INVALID",
            (unsupportedNotification.senderIdentity as MmsNotificationParser.SenderIdentity.Unavailable).reason
        )
    }

    private fun notificationPdu(
        location: String,
        sender: String,
        wrappedUtf8: Boolean = false,
        transactionId: String = DEFAULT_TRANSACTION_ID
    ): ByteArray = notificationPrefix(sender, wrappedUtf8, transactionId) +
        byteArrayOf(0x83.toByte()) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

    private fun notificationPrefix(
        sender: String,
        wrappedUtf8: Boolean = false,
        transactionId: String = DEFAULT_TRANSACTION_ID
    ): ByteArray = byteArrayOf(0x8c.toByte(), 0x82.toByte(), 0x98.toByte()) +
        transactionId.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
        fromHeader(sender, wrappedUtf8)

    private fun fromHeader(sender: String, wrappedUtf8: Boolean = false): ByteArray {
        val address = "$sender/TYPE=PLMN".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val encoded = if (wrappedUtf8) {
            val payload = byteArrayOf(0xea.toByte()) + address // UTF-8 MIB enum 106 as short-integer.
            byteArrayOf(payload.size.toByte()) + payload
        } else {
            address
        }
        val value = byteArrayOf(0x80.toByte()) + encoded
        require(value.size <= 30)
        return byteArrayOf(0x89.toByte(), value.size.toByte()) + value
    }

    private companion object {
        const val DEFAULT_TRANSACTION_ID = "txn-123"
    }
}
