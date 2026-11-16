package com.sentinel.quantum.ai

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SentinelAiAuditJournalTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256") // Fixture only.
    private fun fresh(): SentinelAiAuditJournal = SentinelAiAuditJournal.create(File(temp.root, "audit"), key)

    @Test fun eventsSurviveReopenWithoutPersistingUserContent() {
        val journal = fresh()
        assertEquals(1L, journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, 100).sequence)
        assertEquals(2L, journal.append(SentinelAiAuditJournal.Kind.REVOKED, 120).sequence)
        val reopened = SentinelAiAuditJournal.open(File(temp.root, "audit"), key)
        assertEquals(2L, reopened.verify().sequence)
        assertFalse(File(temp.root, "audit/events").readText().contains("user"))
    }
    @Test fun alteredEventIsRejectedAndNeverOverwritten() {
        val journal = fresh(); journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, 100)
        val events = File(temp.root, "audit/events")
        events.writeText(events.readText().replace("SESSION_OPEN", "REVOKED"))
        val corrupted = events.readBytes()
        assertThrows(IOException::class.java) { journal.append(SentinelAiAuditJournal.Kind.ERROR, 200) }
        assertArrayEquals(corrupted, events.readBytes())
    }
    @Test fun missingAnchorAndMissingEventsBothFailClosed() {
        val journal = fresh();journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, 100)
        val anchor = File(temp.root, "audit/anchor");val saved = anchor.readBytes()
        assertTrue(anchor.delete())
        assertThrows(IOException::class.java) { journal.verify() }
        anchor.writeBytes(saved)
        assertTrue(File(temp.root, "audit/events").delete())
        assertThrows(IOException::class.java) { journal.verify() }
    }
    @Test fun oldEventsWithNewAnchorAreRejected() {
        val journal = fresh();journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, 100)
        val events = File(temp.root, "audit/events");val old = events.readBytes()
        journal.append(SentinelAiAuditJournal.Kind.REVOKED, 200)
        events.writeBytes(old)
        assertThrows(IOException::class.java) { journal.verify() }
    }
    @Test fun wrongKeyNeverResetsExistingJournal() {
        fresh().append(SentinelAiAuditJournal.Kind.SESSION_OPEN, 100)
        val wrong = SecretKeySpec(ByteArray(32) { 42 }, "HmacSHA256")
        assertThrows(IOException::class.java) { SentinelAiAuditJournal.open(File(temp.root, "audit"), wrong).verify() }
    }
    @Test fun newCreationNeverOverwritesExistingDirectory() {
        fresh()
        assertThrows(IOException::class.java) { SentinelAiAuditJournal.create(File(temp.root, "audit"), key) }
    }
    @Test fun capacityLimitPreservesLastVerifiedState() {
        val journal = fresh()
        repeat(SentinelAiAuditJournal.MAX_EVENTS) { journal.append(SentinelAiAuditJournal.Kind.REQUEST, it.toLong()) }
        assertThrows(IOException::class.java) { journal.append(SentinelAiAuditJournal.Kind.REQUEST, 1000) }
        assertEquals(SentinelAiAuditJournal.MAX_EVENTS.toLong(), journal.verify().sequence)
    }
    @Test fun concurrentInstancesDoNotLoseOrDuplicateEvents() {
        val first = fresh(); val second = SentinelAiAuditJournal.open(File(temp.root, "audit"), key)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 20).map { i -> pool.submit<Long> {
                (if (i % 2 == 0) first else second).append(SentinelAiAuditJournal.Kind.REQUEST, 100).sequence
            } }
            assertEquals((1L..20L).toSet(), tasks.map { it.get() }.toSet())
            assertEquals(20L, first.verify().sequence)
        } finally { pool.shutdownNow() }
    }
    @Test fun oversizedFileFailsBeforeParsing() {
        val journal = fresh()
        File(temp.root, "audit/events").writeBytes(ByteArray(200000))
        assertThrows(IOException::class.java) { journal.verify() }
    }
    @Test fun negativeClockIsRejectedWithoutAppending() {
        val journal = fresh()
        assertThrows(IllegalArgumentException::class.java) { journal.append(SentinelAiAuditJournal.Kind.REQUEST, -1) }
        assertEquals(0L, journal.verify().sequence)
    }
}
