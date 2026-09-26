package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.sentinel.quantum.wearable.security.VerifiedWearableHandshakeFixture

class WearableSessionAuthorityTest {
    private val key = "a".repeat(64)
    private fun session(id: String) = WearableSession(id, 1, setOf(WearableCapability.SENTINEL_ALERTS), 100)
    private fun proof(stableId: String, sessionId: String) = VerifiedWearableHandshakeFixture.create(stableId, key, sessionId, 1, setOf("SENTINEL_ALERTS"))

    @Test fun newerHandshakeSupersedesOlderAttemptForSameIdentity() {
        val authority = WearableSessionAuthority()
        val old = WearableHandshakeAttempt("watch-1", "attempt-old", 1)
        val newer = WearableHandshakeAttempt("watch-1", "attempt-new", 2)
        authority.beginHandshake(old)
        authority.beginHandshake(newer)

        assertEquals(WearableSessionActivationDecision.SUPERSEDED_HANDSHAKE,
            authority.activate(old, session("old-session"), proof("watch-1", "old-session")))
        assertEquals(WearableSessionActivationDecision.ACTIVATED,
            authority.activate(newer, session("new-session"), proof("watch-1", "new-session")))
        assertEquals("new-session", authority.activeSession("watch-1")?.sessionId)
    }

    @Test fun activationAtomicallyReplacesPreviousSession() {
        val authority = WearableSessionAuthority()
        val first = WearableHandshakeAttempt("watch-1", "a1", 1)
        authority.beginHandshake(first)
        assertEquals(WearableSessionActivationDecision.ACTIVATED,
            authority.activate(first, session("s1"), proof("watch-1", "s1")))

        val second = WearableHandshakeAttempt("watch-1", "a2", 2)
        authority.beginHandshake(second)
        assertEquals(WearableSessionActivationDecision.ACTIVATED,
            authority.activate(second, session("s2"), proof("watch-1", "s2")))
        assertEquals("s2", authority.activeSession("watch-1")?.sessionId)
    }

    @Test fun differentIdentitiesRemainIndependent() {
        val authority = WearableSessionAuthority()
        val a = WearableHandshakeAttempt("watch-a", "a", 1)
        val b = WearableHandshakeAttempt("watch-b", "b", 1)
        authority.beginHandshake(a)
        authority.beginHandshake(b)
        assertEquals(WearableSessionActivationDecision.ACTIVATED,
            authority.activate(a, session("sa"), proof("watch-a", "sa")))
        assertEquals(WearableSessionActivationDecision.ACTIVATED,
            authority.activate(b, session("sb"), proof("watch-b", "sb")))
        assertEquals("sa", authority.activeSession("watch-a")?.sessionId)
        assertEquals("sb", authority.activeSession("watch-b")?.sessionId)
    }

    @Test fun proofCannotActivateAnotherIdentityOrSession() {
        val authority = WearableSessionAuthority()
        val attempt = WearableHandshakeAttempt("watch-1", "a1", 1)
        authority.beginHandshake(attempt)
        assertEquals(WearableSessionActivationDecision.IDENTITY_MISMATCH,
            authority.activate(attempt, session("s1"), proof("watch-2", "s1")))
        assertEquals(WearableSessionActivationDecision.IDENTITY_MISMATCH,
            authority.activate(attempt, session("s1"), proof("watch-1", "s2")))
        assertNull(authority.activeSession("watch-1"))
    }

    @Test fun revocationClearsPendingAndActiveAuthority() {
        val authority = WearableSessionAuthority()
        val attempt = WearableHandshakeAttempt("watch-1", "a1", 1)
        authority.beginHandshake(attempt)
        authority.activate(attempt, session("s1"), proof("watch-1", "s1"))
        authority.revoke("watch-1")
        assertNull(authority.activeSession("watch-1"))
        assertEquals(WearableSessionActivationDecision.NO_PENDING_HANDSHAKE,
            authority.activate(attempt, session("s1"), proof("watch-1", "s1")))
    }
}
