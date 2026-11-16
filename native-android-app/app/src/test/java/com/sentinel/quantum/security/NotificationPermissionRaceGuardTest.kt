package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class NotificationPermissionRaceGuardTest {
    @Test fun revocationBetweenPermissionCheckAndPublicationDoesNotCrashReceiver() {
        assertFalse(NotificationPermissionRaceGuard.post { throw SecurityException("revoked") })
    }
    @Test fun acceptedPublicationRunsExactlyOnce() {
        var posts = 0
        assertTrue(NotificationPermissionRaceGuard.post { posts++ })
        assertEquals(1, posts)
    }
    @Test fun unrelatedProgrammingErrorsRemainVisible() {
        assertThrows(IllegalStateException::class.java) {
            NotificationPermissionRaceGuard.post { throw IllegalStateException("bug") }
        }
    }
}
