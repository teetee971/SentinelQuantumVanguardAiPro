package com.sentinel.quantum.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOutgoingSubmissionStoreTest {
    private class Preferences {
        val values = linkedMapOf<String, String>()
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor()
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val removals = mutableSetOf<String>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "putString" -> {
                        values[args!![0] as String] = args[1] as String
                        editor
                    }
                    "remove" -> {
                        removals += args!![0] as String
                        editor
                    }
                    "commit" -> {
                        removals.forEach(values::remove)
                        true
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }
    }

    @Test
    fun anUnreconciledSubmissionRemainsPendingAfterRetentionWindow() {
        val preferences = Preferences()
        val store = SmsOutgoingSubmissionStore(preferences.preferences)
        assertTrue(store.register(7, 11L, partCount = 1, nowMs = 1L))

        assertTrue(
            store.hasPendingSubmission(
                nowMs = 1L + 24L * 60L * 60L * 1000L + 1L
            )
        )
    }
}
