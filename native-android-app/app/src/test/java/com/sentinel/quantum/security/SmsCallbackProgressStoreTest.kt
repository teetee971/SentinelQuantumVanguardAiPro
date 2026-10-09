package com.sentinel.quantum.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCallbackProgressStoreTest {
    /** Models Android's memory update even when commit reports a disk failure. */
    private class Preferences(private val failedCommits: Set<Int>) {
        val values = linkedMapOf<String, String>()
        var commits = 0
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val updates = linkedMapOf<String, String?>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "putString" -> {
                        updates[args!![0] as String] = args[1] as String?
                        editor
                    }
                    "remove" -> {
                        updates[args!![0] as String] = null
                        editor
                    }
                    "commit" -> {
                        updates.forEach { (key, value) ->
                            if (value == null) values.remove(key) else values[key] = value
                        }
                        commits++
                        commits !in failedCommits
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }
    }

    @Test fun failedTrimKeepsProviderTransitionAndReportsStorageFailure() {
        val preferences = Preferences(setOf(2))
        repeat(129) { preferences.values["old:$it"] = "1000|0|2||||" }
        verifyTransitionSurvives(preferences)
        // Record, failed trim, then persisted provider acknowledgment.
        assertEquals(3, preferences.commits)
        assertEquals(128, preferences.values.size)
    }

    @Test fun failedCommitKeepsRadioTransitionAndReportsStorageFailure() {
        verifyTransitionSurvives(Preferences(setOf(1)))
    }

    @Test fun failedPruneDoesNotDiscardCurrentRadioTransition() {
        val preferences = Preferences(setOf(1))
        preferences.values["invalid"] = "invalid"
        verifyTransitionSurvives(preferences)
    }

    private fun verifyTransitionSurvives(preferences: Preferences) {
        val store = SmsCallbackProgressStore(preferences.preferences)
        var storageFailure = false
        val outcome = store.record(
            42, 101L, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true,
            nowMs = 1_000_000L, onPersistenceFailure = { storageFailure = true }
        )!!
        assertTrue(outcome.allSent)
        assertTrue(storageFailure)
        var providerUpdated = false
        assertTrue(SmsProviderPersistence.persist(outcome, { true }, {
            providerUpdated = true
            true
        }, { true }))
        assertTrue(providerUpdated)
        assertTrue(store.markProviderApplied(42, 101L, outcome.state, nowMs = 1_000_000L))
        // Retained memory/tombstone cannot justify throwing away the first transition:
        // a repeated callback is correctly ignored.
        assertNull(store.record(42, 101L, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true, nowMs = 1_000_000L))
    }
    @Test fun duplicateCallbackRepairsUnconfirmedProviderWriteEvenAfterTerminalState() {
        val store = SmsCallbackProgressStore(Preferences(emptySet()).preferences)
        store.record(7, 8L, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true, nowMs = 1000L)
        val delivered = store.record(7, 8L, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, true, nowMs = 1001L)!!
        assertTrue(delivered.terminal)
        val retry = store.record(7, 8L, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, true, nowMs = 1002L)!!
        assertTrue(retry.allDelivered)
        assertTrue(retry.certificationSignals.isEmpty())
        assertEquals(1, store.pendingProviderWrites(nowMs = 1002L).size)
        assertTrue(store.markProviderApplied(7, 8L, retry.state, nowMs = 1002L))
        assertNull(store.record(7, 8L, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, true, nowMs = 1003L))
        assertTrue(store.pendingProviderWrites(nowMs = 1003L).isEmpty())
    }

    @Test fun malformedProviderAppliedFlagIsRejectedInsteadOfBecomingPendingState() {
        val preferences = Preferences(emptySet())
        preferences.values["7:8"] = "1000|1|1|0|||x"

        val store = SmsCallbackProgressStore(preferences.preferences)

        assertThrows(IllegalStateException::class.java) {
            store.pendingProviderWrites(nowMs = 1001L)
        }
    }
}

