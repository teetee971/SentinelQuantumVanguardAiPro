package com.sentinel.quantum.auth

import org.junit.Assert.*
import org.junit.Test

class AuthSessionAtomicityTest {

    @Test
    fun staleLoginCannotSaveCredentials() {
        val guard = AuthSessionGeneration()
        val login = guard.begin()
        var saved = false

        guard.invalidateAndRun {}

        val accepted = guard.runIfCurrent(login) {
            saved = true
        }

        assertFalse(accepted)
        assertFalse(saved)
    }

    @Test
    fun currentLoginCanSaveBeforeLogout() {
        val guard = AuthSessionGeneration()
        val login = guard.begin()
        var saved = false
        var cleared = false

        assertTrue(guard.runIfCurrent(login) {
            saved = true
        })

        guard.invalidateAndRun {
            cleared = true
            saved = false
        }

        assertTrue(cleared)
        assertFalse(saved)
        assertFalse(guard.isCurrent(login))
    }
}
