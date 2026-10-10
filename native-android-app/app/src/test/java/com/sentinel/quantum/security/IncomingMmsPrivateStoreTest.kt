package com.sentinel.quantum.security

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsPrivateStoreTest {
    @Test
    fun emptyAndOversizedPdusAreRejectedBeforePersistence() = withTempRoot { root ->
        assertEquals(IncomingMmsPrivateStore.State.FAILED, IncomingMmsPrivateStore.persist(root, ByteArray(0)).state)
        val oversized = ByteArray((MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES + 1L).toInt())
        assertEquals(IncomingMmsPrivateStore.State.FAILED, IncomingMmsPrivateStore.persist(root, oversized).state)
        assertTrue(pduFiles(root).isEmpty())
    }

    @Test
    fun replayUsesOneStableFile() = withTempRoot { root ->
        val pdu = "stable-pdu".toByteArray(Charsets.UTF_8)

        val first = IncomingMmsPrivateStore.persist(root, pdu)
        val second = IncomingMmsPrivateStore.persist(root, pdu.copyOf())

        assertEquals(IncomingMmsPrivateStore.State.CREATED, first.state)
        assertEquals(IncomingMmsPrivateStore.State.EXISTING, second.state)
        assertEquals(first.digestHex, second.digestHex)
        assertEquals(1, pduFiles(root).size)
        assertEquals("${first.digestHex}.pdu", pduFiles(root).single().name)
    }

    @Test
    fun corruptStableIdentityFailsClosedWithoutOverwrite() = withTempRoot { root ->
        val pdu = "trusted-pdu".toByteArray(Charsets.UTF_8)
        val digest = IncomingMmsIdentity.sha256Hex(pdu)
        assertNotNull(digest)
        val inbox = File(root, "mms-inbox").apply { mkdirs() }
        val target = File(inbox, "$digest.pdu")
        target.writeText("corrupt")
        val before = target.readBytes()

        val result = IncomingMmsPrivateStore.persist(root, pdu)

        assertEquals(IncomingMmsPrivateStore.State.FAILED, result.state)
        assertTrue(before.contentEquals(target.readBytes()))
        assertEquals(1, pduFiles(root).size)
    }

    @Test
    fun stalePartialIsRemovedBeforeCommittedWrite() = withTempRoot { root ->
        val inbox = File(root, "mms-inbox").apply { mkdirs() }
        val residue = File(inbox, "${"a".repeat(64)}.part")
        residue.writeText("crash-residue")

        val result = IncomingMmsPrivateStore.persist(root, byteArrayOf(1, 2, 3, 4))

        assertEquals(IncomingMmsPrivateStore.State.CREATED, result.state)
        assertFalse(residue.exists())
        assertTrue(inbox.listFiles().orEmpty().none { it.extension == "part" })
    }

    @Test
    fun concurrentSamePduCreatesExactlyOneCommittedFile() = withTempRoot { root ->
        val workers = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val states = java.util.Collections.synchronizedList(
            mutableListOf<IncomingMmsPrivateStore.State>()
        )
        val pdu = ByteArray(4096) { (it and 0xff).toByte() }

        repeat(2) {
            workers.execute {
                start.await()
                states += IncomingMmsPrivateStore.persist(root, pdu).state
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        workers.shutdownNow()

        assertEquals(2, states.size)
        assertEquals(1, states.count { it == IncomingMmsPrivateStore.State.CREATED })
        assertEquals(1, states.count { it == IncomingMmsPrivateStore.State.EXISTING })
        assertEquals(1, pduFiles(root).size)
    }

    @Test
    fun retentionNeverExceedsBound() = withTempRoot { root ->
        repeat(IncomingMmsPrivateStore.MAX_STORED_MMS + 4) { index ->
            val pdu = "pdu-$index".toByteArray(Charsets.UTF_8)
            val result = IncomingMmsPrivateStore.persist(root, pdu)
            assertEquals(IncomingMmsPrivateStore.State.CREATED, result.state)
        }

        assertEquals(IncomingMmsPrivateStore.MAX_STORED_MMS, pduFiles(root).size)
        assertTrue(File(root, "mms-inbox").listFiles().orEmpty().none { it.extension == "part" })
    }

    private fun pduFiles(root: File): List<File> =
        File(root, "mms-inbox").listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            .orEmpty()

    private fun withTempRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("sentinel-mms-store-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
