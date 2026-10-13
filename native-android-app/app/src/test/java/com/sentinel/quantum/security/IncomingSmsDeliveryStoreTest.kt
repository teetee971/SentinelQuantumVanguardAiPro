package com.sentinel.quantum.security

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingSmsDeliveryStoreTest {
    @Test
    fun `identity is stable and binds PDU order plus subscription`() {
        val pdus = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        val first = IncomingSmsDeliveryStore.identity(pdus, "+590690000000", "bonjour", 1234L, 1)
        val replay = IncomingSmsDeliveryStore.identity(pdus.map { it.copyOf() }, "+590690000000", "bonjour", 1234L, 1)
        val reversed = IncomingSmsDeliveryStore.identity(pdus.reversed(), "+590690000000", "bonjour", 1234L, 1)
        val otherSubscription = IncomingSmsDeliveryStore.identity(pdus, "+590690000000", "bonjour", 1234L, 2)

        assertEquals(first, replay)
        assertNotEquals(first, reversed)
        assertNotEquals(first, otherSubscription)
        assertTrue(first!!.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `durable record round trips and replay is idempotent`() {
        val root = Files.createTempDirectory("sentinel-sms-ingress").toFile()
        try {
            val record = record("a".repeat(64), 10L)
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CREATED,
                IncomingSmsDeliveryStore.persist(root, record)
            )
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.EXISTING,
                IncomingSmsDeliveryStore.persist(root, record)
            )
            assertEquals(record, IncomingSmsDeliveryStore.read(root, record.id))
            assertEquals(listOf(record.id), IncomingSmsDeliveryStore.pendingIds(root))
            assertTrue(IncomingSmsDeliveryStore.delete(root, record.id))
            assertNull(IncomingSmsDeliveryStore.read(root, record.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `capacity is bounded without overwriting existing staged message`() {
        val root = Files.createTempDirectory("sentinel-sms-capacity").toFile()
        try {
            val first = record("b".repeat(64), 20L)
            val second = record("c".repeat(64), 21L)
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CREATED,
                IncomingSmsDeliveryStore.persist(root, first, maxPendingRecords = 1)
            )
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.EXISTING,
                IncomingSmsDeliveryStore.persist(root, first, maxPendingRecords = 1)
            )
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CAPACITY_EXCEEDED,
                IncomingSmsDeliveryStore.persist(root, second, maxPendingRecords = 1)
            )
            assertEquals(first, IncomingSmsDeliveryStore.read(root, first.id))
            assertNull(IncomingSmsDeliveryStore.read(root, second.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `corrupt staged record is never accepted as replay or permanent capacity`() {
        val root = Files.createTempDirectory("sentinel-sms-corrupt").toFile()
        try {
            val first = record("d".repeat(64), 30L)
            val second = record("e".repeat(64), 31L)
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CREATED,
                IncomingSmsDeliveryStore.persist(root, first, maxPendingRecords = 1)
            )

            val firstFile = root.walkTopDown().first { it.name == first.id + ".sms" }
            firstFile.writeText("corrupt")
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CREATED,
                IncomingSmsDeliveryStore.persist(root, first, maxPendingRecords = 1)
            )
            assertEquals(first, IncomingSmsDeliveryStore.read(root, first.id))

            val rewritten = root.walkTopDown().first { it.name == first.id + ".sms" }
            rewritten.writeText("corrupt-again")
            assertEquals(
                IncomingSmsDeliveryStore.PersistState.CREATED,
                IncomingSmsDeliveryStore.persist(root, second, maxPendingRecords = 1)
            )
            assertNull(IncomingSmsDeliveryStore.read(root, first.id))
            assertEquals(second, IncomingSmsDeliveryStore.read(root, second.id))
            assertEquals(listOf(second.id), IncomingSmsDeliveryStore.pendingIds(root))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun record(id: String, receivedAt: Long) = IncomingSmsDeliveryStore.Record(
        id = id,
        address = "+590690000000",
        body = "message test",
        receivedAtMs = receivedAt,
        sentAtMs = receivedAt - 1,
        subscriptionId = 1
    )
}
