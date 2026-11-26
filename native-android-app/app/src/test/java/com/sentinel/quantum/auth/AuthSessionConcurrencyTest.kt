package com.sentinel.quantum.auth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class AuthSessionConcurrencyTest {

    @Test
    fun logoutCannotInterleaveWithCredentialSave() {
        val guard = AuthSessionGeneration()
        val generation = guard.begin()

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)

        val saved = AtomicBoolean(false)
        val cleared = AtomicBoolean(false)

        val writer = Thread {
            guard.runIfCurrent(generation) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                saved.set(true)
            }
        }

        val logout = Thread {
            guard.invalidateAndRun {
                cleared.set(true)
                saved.set(false)
            }
            finished.countDown()
        }

        writer.start()

        assertTrue(entered.await(5, TimeUnit.SECONDS))

        logout.start()

        try {
            assertFalse(
                "Logout must wait for credential save",
                finished.await(100, TimeUnit.MILLISECONDS)
            )
        } finally {
            release.countDown()
            writer.join(5000)
            logout.join(5000)
        }

        assertFalse(writer.isAlive)
        assertFalse(logout.isAlive)
        assertTrue(cleared.get())
        assertFalse(saved.get())
        assertFalse(guard.isCurrent(generation))
    }

    @Test
    fun staleCredentialSaveIsRejectedAfterLogout() {
        val guard = AuthSessionGeneration()
        val generation = guard.begin()
        val saved = AtomicBoolean(false)

        guard.invalidateAndRun {}

        val accepted = guard.runIfCurrent(generation) {
            saved.set(true)
        }

        assertFalse(accepted)
        assertFalse(saved.get())
    }
}
