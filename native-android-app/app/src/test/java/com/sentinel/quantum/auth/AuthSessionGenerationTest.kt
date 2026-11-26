package com.sentinel.quantum.auth

import org.junit.Assert.*
import org.junit.Test

class AuthSessionGenerationTest {
    @Test
    fun logoutInvalidatesPreviousLogin() {
        val guard = AuthSessionGeneration()
        val login = guard.begin()
        guard.invalidate()
        assertFalse(guard.isCurrent(login))
    }

    @Test
    fun latestLoginRemainsValid() {
        val guard = AuthSessionGeneration()
        guard.begin()
        val latest = guard.begin()
        assertTrue(guard.isCurrent(latest))
    }

    @Test
    fun oldRestoreCannotOverwriteNewSession() {
        val guard = AuthSessionGeneration()
        val restore = guard.begin()
        val login = guard.begin()
        assertFalse(guard.isCurrent(restore))
        assertTrue(guard.isCurrent(login))
    }
}
