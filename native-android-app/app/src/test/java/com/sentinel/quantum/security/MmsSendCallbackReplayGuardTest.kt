package com.sentinel.quantum.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendCallbackReplayGuardTest {
    private val token = "11111111-1111-4111-8111-111111111111"

    @Test
    fun malformedTombstoneFailsClosedInsteadOfBeingTreatedAsAbsent() {
        val preferences = Preferences(mapOf(token to "not-a-timestamp"))

        assertFalse(MmsSendCallbackReplayGuard(preferences.shared).acceptOnce(token, nowMs = 10_000L))
        assertEquals("not-a-timestamp", preferences.values[token])
    }

    @Test
    fun validTombstoneIsAcceptedOnceAndStoredAsLong() {
        val preferences = Preferences()
        val guard = MmsSendCallbackReplayGuard(preferences.shared)

        assertTrue(guard.acceptOnce(token, nowMs = 10_000L))
        assertFalse(guard.acceptOnce(token, nowMs = 10_001L))
        assertEquals(10_000L, preferences.values[token])
    }

    private class Preferences(initial: Map<String, Any> = emptyMap()) {
        val values = linkedMapOf<String, Any>().apply { putAll(initial) }
        val shared = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor()
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = linkedMapOf<String, Any>()
            val removals = mutableSetOf<String>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "putLong" -> {
                        pending[args!![0] as String] = args[1] as Long
                        editor
                    }
                    "remove" -> {
                        removals += args!![0] as String
                        editor
                    }
                    "commit" -> {
                        removals.forEach(values::remove)
                        values.putAll(pending)
                        true
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }
    }
}
