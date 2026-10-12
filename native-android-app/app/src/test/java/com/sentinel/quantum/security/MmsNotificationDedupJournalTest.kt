package com.sentinel.quantum.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class MmsNotificationDedupJournalTest {
    @Test fun duplicateNotificationIsRejectedUntilReservationIsReleased() {
        val preferences = Preferences()
        val journal = MmsNotificationDedupJournal(preferences.preferences)
        val identity = "a".repeat(64)
        val firstFile = "123e4567-e89b-12d3-a456-426614174000.pdu"
        val secondFile = "123e4567-e89b-12d3-a456-426614174001.pdu"

        assertEquals(
            MmsNotificationDedupJournal.Reservation.ACQUIRED,
            journal.reserve(identity, firstFile, requestedAtMs = 1_000L)
        )
        assertEquals(
            MmsNotificationDedupJournal.Reservation.DUPLICATE,
            journal.reserve(identity, secondFile, requestedAtMs = 1_001L)
        )

        assertEquals(true, journal.release(firstFile))
        assertEquals(
            MmsNotificationDedupJournal.Reservation.ACQUIRED,
            journal.reserve(identity, secondFile, requestedAtMs = 1_002L)
        )
    }

    @Test fun expiredNotificationReservationCanBeReplaced() {
        val preferences = Preferences()
        val journal = MmsNotificationDedupJournal(preferences.preferences)
        val identity = "b".repeat(64)
        val firstFile = "123e4567-e89b-12d3-a456-426614174000.pdu"
        val secondFile = "123e4567-e89b-12d3-a456-426614174001.pdu"

        assertEquals(
            MmsNotificationDedupJournal.Reservation.ACQUIRED,
            journal.reserve(identity, firstFile, requestedAtMs = 1_000L)
        )
        assertEquals(
            MmsNotificationDedupJournal.Reservation.ACQUIRED,
            journal.reserve(
                identity,
                secondFile,
                requestedAtMs = 1_000L + MmsNotificationDedupJournal.TTL_MS
            )
        )
    }

    @Test fun completedReplayMarkersDoNotExhaustActiveReservationCapacity() {
        val preferences = Preferences()
        val journal = MmsNotificationDedupJournal(preferences.preferences)
        val firstIdentity = "%064x".format(1)
        val firstFile = "123e4567-e89b-12d3-a456-426614174001.pdu"

        for (index in 1..MmsNotificationDedupJournal.MAX_RECORDS) {
            val identity = "%064x".format(index)
            val fileName = "123e4567-e89b-12d3-a456-%012d.pdu".format(index)
            assertEquals(
                MmsNotificationDedupJournal.Reservation.ACQUIRED,
                journal.reserve(identity, fileName, requestedAtMs = 1_000L + index)
            )
            assertEquals(true, journal.complete(fileName))
        }

        assertEquals(
            MmsNotificationDedupJournal.Reservation.ACQUIRED,
            journal.reserve(
                "%064x".format(MmsNotificationDedupJournal.MAX_RECORDS + 1),
                "123e4567-e89b-12d3-a456-426614174999.pdu",
                requestedAtMs = 2_000L
            )
        )
        assertEquals(
            MmsNotificationDedupJournal.Reservation.DUPLICATE,
            journal.reserve(
                firstIdentity,
                "123e4567-e89b-12d3-a456-426614175000.pdu",
                requestedAtMs = 2_001L
            )
        )
    }

    private class Preferences {
        val values = linkedMapOf<String, String>()
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAll" -> values.toMap()
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
}
