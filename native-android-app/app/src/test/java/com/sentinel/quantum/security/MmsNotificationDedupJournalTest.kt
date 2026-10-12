package com.sentinel.quantum.security

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

    @Test fun concurrentJournalInstancesCannotReserveSameNotification() {
        val preferences = RacingPreferences()
        val firstJournal = MmsNotificationDedupJournal(preferences.preferences)
        val secondJournal = MmsNotificationDedupJournal(preferences.preferences)
        val identity = "c".repeat(64)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val results = mutableListOf<MmsNotificationDedupJournal.Reservation>()

        fun launch(journal: MmsNotificationDedupJournal, fileName: String) = Thread {
            start.await()
            val result = journal.reserve(identity, fileName, requestedAtMs = 5_000L)
            synchronized(results) { results += result }
            done.countDown()
        }.also { it.start() }

        launch(firstJournal, "123e4567-e89b-12d3-a456-426614174010.pdu")
        launch(secondJournal, "123e4567-e89b-12d3-a456-426614174011.pdu")
        start.countDown()

        assertEquals(true, done.await(8, TimeUnit.SECONDS))
        assertEquals(1, results.count { it == MmsNotificationDedupJournal.Reservation.ACQUIRED })
        assertEquals(1, results.count { it == MmsNotificationDedupJournal.Reservation.DUPLICATE })
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

    private class RacingPreferences {
        private val values = ConcurrentHashMap<String, String>()
        private val firstReadBarrier = CountDownLatch(2)
        private val secondReadBarrier = CountDownLatch(2)
        private val concurrentPairObserved = AtomicBoolean(false)
        private val readsByThread = ConcurrentHashMap<Long, AtomicInteger>()

        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAll" -> {
                    val reads = readsByThread.computeIfAbsent(Thread.currentThread().id) {
                        AtomicInteger()
                    }.incrementAndGet()
                    if (reads == 1) {
                        firstReadBarrier.countDown()
                        if (firstReadBarrier.await(1, TimeUnit.SECONDS)) {
                            concurrentPairObserved.set(true)
                        }
                    } else if (reads == 2 && concurrentPairObserved.get()) {
                        secondReadBarrier.countDown()
                        check(secondReadBarrier.await(2, TimeUnit.SECONDS)) {
                            "Both unsynchronized journal transactions did not reach the read-check-write window"
                        }
                    }
                    values.toMap()
                }
                "edit" -> editor()
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val writes = linkedMapOf<String, String>()
            val removals = mutableSetOf<String>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "putString" -> {
                        writes[args!![0] as String] = args[1] as String
                        editor
                    }
                    "remove" -> {
                        removals += args!![0] as String
                        editor
                    }
                    "commit" -> {
                        removals.forEach(values::remove)
                        values.putAll(writes)
                        true
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }
    }
}
