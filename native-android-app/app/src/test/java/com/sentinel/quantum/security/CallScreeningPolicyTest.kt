package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class CallScreeningPolicyTest {
    @Test fun missingPreferenceDoesNotSilenceExistingUsers() {
        assertFalse(SilenceOutsideDirectoryPreference.resolved(null))
        val engine = CallRuleEngine(
            silenceOutsideDirectory = SilenceOutsideDirectoryPreference.resolved(null),
            directoryReady = true,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }

    @Test fun explicitChoiceIsPreservedAndTakesEffectWithoutClearingTheDirectory() {
        assertTrue(SilenceOutsideDirectoryPreference.resolved(true))
        assertFalse(SilenceOutsideDirectoryPreference.resolved(false))
        val disabled = CallRuleEngine(
            silenceOutsideDirectory = SilenceOutsideDirectoryPreference.resolved(false),
            directoryReady = true,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, disabled.evaluate("+33612345678").action)
        val enabled = CallRuleEngine(
            silenceOutsideDirectory = SilenceOutsideDirectoryPreference.resolved(true),
            directoryReady = true,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.SILENCE, enabled.evaluate("+33612345678").action)
        val notReady = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = false,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, notReady.evaluate("+33612345678").action)
    }

    @Test fun silenceRequiresAndroid10AndBlockRejectsOnOlderPlatforms() {
        assertEquals(
            CallScreeningPlatformEffect.REJECT,
            callScreeningPlatformEffect(CallRuleEngine.Action.BLOCK, 24)
        )
        assertEquals(
            CallScreeningPlatformEffect.REJECT,
            callScreeningPlatformEffect(CallRuleEngine.Action.BLOCK, 37)
        )
        assertEquals(
            CallScreeningPlatformEffect.ALLOW,
            callScreeningPlatformEffect(CallRuleEngine.Action.SILENCE, 28)
        )
        assertEquals(
            CallScreeningPlatformEffect.SILENCE,
            callScreeningPlatformEffect(CallRuleEngine.Action.SILENCE, 29)
        )
        assertEquals(
            CallScreeningPlatformEffect.SILENCE,
            callScreeningPlatformEffect(CallRuleEngine.Action.SILENCE, 36)
        )
        assertEquals(
            CallScreeningPlatformEffect.ALLOW,
            callScreeningPlatformEffect(CallRuleEngine.Action.ALLOW, 35)
        )
    }

    @Test fun emergencyUnknownOrTrueFailsOpen() {
        assertTrue(screeningMustFailOpen(null))
        assertTrue(screeningMustFailOpen(true))
        assertFalse(screeningMustFailOpen(false))
    }

    @Test fun coldDirectoryIsNotReady() {
        val gate = DirectoryPublication()
        assertFalse(gate.current().ready)
        assertTrue(gate.current().numbers.isEmpty())
    }

    @Test fun directoryCapFailsOpenInsteadOfKeepingAPartialSet() {
        assertTrue(DirectoryPublication.hasRoom(DirectoryPublication.MAX_NUMBERS - 1))
        assertFalse(DirectoryPublication.hasRoom(DirectoryPublication.MAX_NUMBERS))
    }

    @Test fun olderRefreshCannotRepublishAfterRevocation() {
        val gate = DirectoryPublication()
        val ticket = gate.openRefresh()
        gate.closeBecausePermissionMissing()
        val published = gate.publish(
            ticket,
            DirectoryPublication.View(true, setOf("+590690123456")),
            permissionGranted = true
        )
        assertFalse(published)
        assertFalse(gate.current().ready)
        assertTrue(gate.current().numbers.isEmpty())
    }

    @Test fun readyPublishIsDroppedWhenPermissionIsAlreadyGone() {
        val gate = DirectoryPublication()
        val ticket = gate.openRefresh()
        assertTrue(
            gate.publish(
                ticket,
                DirectoryPublication.View(true, setOf("+590690123456")),
                permissionGranted = false
            )
        )
        assertFalse(gate.current().ready)
    }

    @Test fun newerContactReloadWinsOverAnOlderOne() {
        val gate = DirectoryPublication()
        val first = gate.openRefresh()
        val second = gate.openRefresh()
        assertTrue(gate.publish(second, DirectoryPublication.View(true, setOf("+590690123456")), true))
        assertFalse(gate.publish(first, DirectoryPublication.View(true, setOf("+33612345678")), true))
        assertEquals(setOf("+590690123456"), gate.current().numbers)
    }

    @Test fun disablingPreferencePublishesAnEmptyViewWithoutANewerTicket() {
        val gate = DirectoryPublication()
        val loaded = gate.openRefresh()
        assertTrue(gate.publish(loaded, DirectoryPublication.View(true, setOf("+590690123456")), true))
        val disabled = gate.openRefresh()
        assertTrue(gate.publish(disabled, DirectoryPublication.View(false, emptySet()), true))
        assertFalse(gate.publish(loaded, DirectoryPublication.View(true, setOf("+590690123456")), true))
        assertFalse(gate.current().ready)
    }

    @Test fun concurrentRevocationAlwaysEndsClosed() {
        val failures = AtomicInteger()
        repeat(40) {
            val gate = DirectoryPublication()
            val ticket = gate.openRefresh()
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            Thread {
                start.await()
                gate.publish(ticket, DirectoryPublication.View(true, setOf("x")), true)
                done.countDown()
            }.start()
            Thread {
                start.await()
                gate.closeBecausePermissionMissing()
                done.countDown()
            }.start()
            start.countDown()
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            if (gate.current().ready || gate.current().numbers.isNotEmpty()) failures.incrementAndGet()
        }
        assertEquals(0, failures.get())
    }
}
