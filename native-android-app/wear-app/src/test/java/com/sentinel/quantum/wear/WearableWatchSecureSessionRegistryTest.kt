package com.sentinel.quantum.wear

import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableWatchSecureSessionRegistryTest {
    @Test fun sessionsAreBoundToNodeAndSessionAndExpireFailClosed() {
        var now = 100L
        val registry = WearableWatchSecureSessionRegistry(
            maxSessions = 2,
            ttlMs = 1_000L,
            clockMs = { now }
        )
        val session = fakeSession("one")

        assertTrue(registry.register("node-1", "session-1", session))
        assertSame(session, registry.resolve("node-1", "session-1"))
        assertNull(registry.resolve("node-2", "session-1"))
        assertNull(registry.resolve("node-1", "session-2"))

        now = 1_101L
        assertNull(registry.resolve("node-1", "session-1"))
    }

    @Test fun defaultSessionLifetimeCoversFiveMinutePairingConfirmationWindow() {
        var now = 100L
        val registry = WearableWatchSecureSessionRegistry(clockMs = { now })
        val session = fakeSession("pairing")

        assertTrue(registry.register("node-1", "session-1", session))
        now = 299_999L
        assertSame(session, registry.resolve("node-1", "session-1"))
    }

    @Test fun acceptedLivenessExtendsSessionExpiryWithoutWeakeningReplayGuard() {
        var now = 100L
        val registry = WearableWatchSecureSessionRegistry(
            maxSessions = 2,
            ttlMs = 1_000L,
            clockMs = { now }
        )
        val session = fakeSession("one")
        assertTrue(registry.register("node-1", "session-1", session))

        now = 900L
        assertTrue(registry.acceptLivenessSequence("node-1", "session-1", 1L))
        assertFalse(registry.acceptLivenessSequence("node-1", "session-1", 1L))

        now = 1_200L
        assertSame(session, registry.resolve("node-1", "session-1"))
        assertTrue(registry.acceptLivenessSequence("node-1", "session-1", 2L))

        now = 2_201L
        assertNull(registry.resolve("node-1", "session-1"))
    }

    @Test fun newerSessionForSameNodeSupersedesOlderSession() {
        val registry = WearableWatchSecureSessionRegistry(maxSessions = 2, ttlMs = 5_000L, clockMs = { 100L })
        val oldSession = fakeSession("old")
        val newSession = fakeSession("new")

        assertTrue(registry.register("node-1", "session-old", oldSession))
        assertTrue(registry.register("node-1", "session-new", newSession))

        assertNull(registry.resolve("node-1", "session-old"))
        assertSame(newSession, registry.resolve("node-1", "session-new"))
        assertSame(newSession, registry.resolveNode("node-1")?.session)
        assertTrue(registry.resolveNode("node-1")?.sessionId == "session-new")
    }

    @Test fun registryIsBoundedAndDisconnectRevokesAllNodeSessions() {
        val registry = WearableWatchSecureSessionRegistry(maxSessions = 2, ttlMs = 5_000L, clockMs = { 100L })
        assertTrue(registry.register("node-1", "session-1", fakeSession("one")))
        assertTrue(registry.register("node-2", "session-2", fakeSession("two")))
        assertFalse(registry.register("node-3", "session-3", fakeSession("three")))

        registry.revokeNode("node-1")
        assertNull(registry.resolve("node-1", "session-1"))
        assertTrue(registry.register("node-3", "session-3", fakeSession("three")))
    }

    private fun fakeSession(label: String): WearableHpkeDuplexSession {
        val wearable = "wearable-$label".toByteArray()
        val phone = "phone-$label".toByteArray()
        return object : WearableHpkeDuplexSession {
            override val wearablePublicKeyEncoded = wearable
            override val wearableFingerprintSha256 = sha256(wearable)
            override val phonePublicKeyEncoded = phone
            override val phoneFingerprintSha256 = sha256(phone)
            override fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray) = plaintext.copyOf()
            override fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray) = ciphertext.copyOf()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
