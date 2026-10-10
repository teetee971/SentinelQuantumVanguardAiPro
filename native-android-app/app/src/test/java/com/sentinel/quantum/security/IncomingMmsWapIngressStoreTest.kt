package com.sentinel.quantum.security

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsWapIngressStoreTest {
    @Test
    fun persistReadAndDeleteAreDigestBoundAndIdempotent() = withTempRoot { root ->
        val pdu = "wap-ingress".toByteArray(Charsets.UTF_8)

        val first = IncomingMmsWapIngressStore.persist(root, pdu)
        val second = IncomingMmsWapIngressStore.persist(root, pdu.copyOf())

        assertEquals(IncomingMmsWapIngressStore.State.CREATED, first.state)
        assertEquals(IncomingMmsWapIngressStore.State.EXISTING, second.state)
        assertEquals(pdu.toList(), IncomingMmsWapIngressStore.read(root, first.digestHex!!)?.toList())
        assertTrue(IncomingMmsWapIngressStore.delete(root, first.digestHex))
        assertNull(IncomingMmsWapIngressStore.read(root, first.digestHex))
    }

    @Test
    fun emptyOversizedAndCorruptPdusFailClosed() = withTempRoot { root ->
        assertEquals(
            IncomingMmsWapIngressStore.State.FAILED,
            IncomingMmsWapIngressStore.persist(root, ByteArray(0)).state
        )
        assertEquals(
            IncomingMmsWapIngressStore.State.FAILED,
            IncomingMmsWapIngressStore.persist(root, ByteArray(512 * 1024 + 1)).state
        )

        val pdu = "trusted".toByteArray(Charsets.UTF_8)
        val digest = IncomingMmsIdentity.sha256Hex(pdu)!!
        val inbox = File(root, "mms-wap-inbox").apply { mkdirs() }
        File(inbox, "$digest.pdu").writeText("corrupt")

        assertEquals(
            IncomingMmsWapIngressStore.State.FAILED,
            IncomingMmsWapIngressStore.persist(root, pdu).state
        )
        assertFalse(IncomingMmsWapIngressStore.read(root, digest) != null)
    }

    private fun withTempRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("sentinel-mms-wap-store-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
