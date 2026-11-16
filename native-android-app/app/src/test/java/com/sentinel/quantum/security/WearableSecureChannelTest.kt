package com.sentinel.quantum.security

import java.security.GeneralSecurityException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearableSecureChannelTest {
    private class ContextCrypto : WearableSecureCryptoEndpoint {
        override fun encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray =
            contextInfo.size.toString().toByteArray() + byteArrayOf(0) +
                MessageDigest.getInstance("SHA-256").digest(contextInfo) + plaintext

        override fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray {
            val zero = ciphertext.indexOf(0)
            if (zero <= 0 || ciphertext.size < zero + 1 + 32) throw GeneralSecurityException("bad frame")
            val digestOffset = zero + 1
            val actual = ciphertext.copyOfRange(digestOffset, digestOffset + 32)
            val expected = MessageDigest.getInstance("SHA-256").digest(contextInfo)
            if (!MessageDigest.isEqual(actual, expected)) throw GeneralSecurityException("wrong context")
            return ciphertext.copyOfRange(digestOffset + 32, ciphertext.size)
        }
    }

    private fun session() = WearableSession(
        sessionId = "session-v2",
        protocolVersion = 2,
        negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
        establishedAtMs = 100_000L
    )

    private fun channel(localRole: WearablePeerRole = WearablePeerRole.PHONE) = WearableSecureChannel(
        session = session(),
        localRole = localRole,
        crypto = ContextCrypto(),
        messageGuard = WearableMessageGuard(maxMessageAgeMs = 30_000L, maxFutureSkewMs = 5_000L)
    )

    private fun envelope(
        sequence: Long = 1,
        sentAt: Long = 110_000L,
        capability: WearableCapability? = WearableCapability.SENTINEL_ALERTS
    ) = WearableMessageEnvelope(
        sessionId = "session-v2",
        protocolVersion = 2,
        sequenceNumber = sequence,
        sentAtMs = sentAt,
        requiredCapability = capability
    )

    @Test
    fun roundTripAcceptsPayloadOnlyAfterGuard() {
        val phone = channel(WearablePeerRole.PHONE)
        val watch = channel(WearablePeerRole.WEARABLE)
        val payload = "risk-alert".toByteArray()

        val ciphertext = watch.seal(payload, envelope(), WearablePeerRole.WEARABLE)
        val opened = phone.open(
            ciphertext,
            expectedCapability = WearableCapability.SENTINEL_ALERTS,
            senderRole = WearablePeerRole.WEARABLE,
            nowMs = 110_001L
        )

        assertEquals(WearableSecureMessageState.ACCEPTED, opened.state)
        assertArrayEquals(payload, opened.payload)
        assertEquals(WearableMessageDecision.ACCEPT, opened.guardDecision)
    }

    @Test
    fun wrongSessionRoleOrCapabilityContextFailsClosed() {
        val watch = channel(WearablePeerRole.WEARABLE)
        val ciphertext = watch.seal("x".toByteArray(), envelope(), WearablePeerRole.WEARABLE)
        val phone = channel(WearablePeerRole.PHONE)

        assertEquals(WearableSecureMessageState.REJECTED,
            phone.open(ciphertext, null, WearablePeerRole.WEARABLE, 110_001L).state)
        assertEquals(WearableSecureMessageState.REJECTED,
            phone.open(ciphertext, WearableCapability.SENTINEL_ALERTS, WearablePeerRole.PHONE, 110_001L).state)

        val otherSession = WearableSecureChannel(
            session = session().copy(sessionId = "other-session"),
            localRole = WearablePeerRole.PHONE,
            crypto = ContextCrypto(),
            messageGuard = WearableMessageGuard()
        )
        assertEquals(WearableSecureMessageState.REJECTED,
            otherSession.open(ciphertext, WearableCapability.SENTINEL_ALERTS, WearablePeerRole.WEARABLE, 110_001L).state)
    }

    @Test
    fun duplicateOutOfOrderExpiredAndFutureMessagesAreRejected() {
        val watch = channel(WearablePeerRole.WEARABLE)
        val phone = channel(WearablePeerRole.PHONE)
        fun send(e: WearableMessageEnvelope, now: Long) = phone.open(
            watch.seal("x".toByteArray(), e, WearablePeerRole.WEARABLE),
            WearableCapability.SENTINEL_ALERTS,
            WearablePeerRole.WEARABLE,
            now
        )

        assertEquals(WearableSecureMessageState.ACCEPTED, send(envelope(sequence = 2), 110_001L).state)
        assertEquals(WearableMessageDecision.DUPLICATE_OR_REPLAY,
            send(envelope(sequence = 2), 110_002L).guardDecision)
        assertEquals(WearableMessageDecision.DUPLICATE_OR_REPLAY,
            send(envelope(sequence = 1), 110_003L).guardDecision)
        assertEquals(WearableMessageDecision.EXPIRED,
            send(envelope(sequence = 3, sentAt = 50_000L), 110_004L).guardDecision)
        assertEquals(WearableMessageDecision.FUTURE_TIMESTAMP,
            send(envelope(sequence = 4, sentAt = 120_000L), 110_004L).guardDecision)
    }

    @Test
    fun malformedOrOversizedCiphertextNeverProducesPayload() {
        val phone = channel()
        val malformed = phone.open(
            byteArrayOf(1, 2, 3),
            WearableCapability.SENTINEL_ALERTS,
            WearablePeerRole.WEARABLE,
            110_000L
        )
        assertEquals(WearableSecureMessageState.REJECTED, malformed.state)
        assertNull(malformed.payload)

        val oversized = phone.open(
            ByteArray(WearableSecureChannel.MAX_CIPHERTEXT_BYTES + 1),
            WearableCapability.SENTINEL_ALERTS,
            WearablePeerRole.WEARABLE,
            110_000L
        )
        assertEquals(WearableSecureMessageState.REJECTED, oversized.state)
        assertNull(oversized.payload)
    }
}
