package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MmsImagePartContractTest {

    @Test
    fun resolvesSyntheticMmsIdentity() {
        val identity = MmsImagePartContract.resolveMessage(
            syntheticMessageId = -42L,
            threadId = 7L
        )

        assertNotNull(identity)
        assertEquals(42L, identity!!.providerMessageId)
        assertEquals(7L, identity.threadId)
        assertEquals(-42L, identity.syntheticMessageId)
    }

    @Test
    fun rejectsSmsAndInvalidMessageIdentities() {
        assertNull(
            MmsImagePartContract.resolveMessage(42L, 7L)
        )
        assertNull(
            MmsImagePartContract.resolveMessage(0L, 7L)
        )
        assertNull(
            MmsImagePartContract.resolveMessage(
                Long.MIN_VALUE, 7L
            )
        )
        assertNull(
            MmsImagePartContract.resolveMessage(-42L, 0L)
        )
        assertNull(
            MmsImagePartContract.resolveMessage(-42L, -1L)
        )
    }

    @Test
    fun acceptsSupportedImageTypes() {
        val message = requireNotNull(
            MmsImagePartContract.resolveMessage(-42L, 7L)
        )

        for (mime in listOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            " IMAGE/JPEG "
        )) {
            val key = MmsImagePartContract.acceptPart(
                message,
                MmsImagePartContract.PartMetadata(
                    partId = 3L,
                    parentMessageId = 42L,
                    mimeType = mime
                )
            )

            assertNotNull("Rejected: $mime", key)
            assertEquals(42L, key!!.providerMessageId)
            assertEquals(7L, key.threadId)
            assertEquals(3L, key.partId)
        }
    }

    @Test
    fun rejectsPartsFromAnotherMessage() {
        val message = requireNotNull(
            MmsImagePartContract.resolveMessage(-42L, 7L)
        )

        assertNull(
            MmsImagePartContract.acceptPart(
                message,
                MmsImagePartContract.PartMetadata(
                    partId = 3L,
                    parentMessageId = 99L,
                    mimeType = "image/jpeg"
                )
            )
        )
    }

    @Test
    fun rejectsUnsupportedMimeTypes() {
        val message = requireNotNull(
            MmsImagePartContract.resolveMessage(-42L, 7L)
        )

        for (mime in listOf(
            null,
            "",
            "text/plain",
            "application/smil",
            "image/svg+xml",
            "application/octet-stream"
        )) {
            assertNull(
                MmsImagePartContract.acceptPart(
                    message,
                    MmsImagePartContract.PartMetadata(
                        partId = 3L,
                        parentMessageId = 42L,
                        mimeType = mime
                    )
                )
            )
        }
    }

    @Test
    fun rejectsInvalidPartIdentifiers() {
        val message = requireNotNull(
            MmsImagePartContract.resolveMessage(-42L, 7L)
        )

        for (id in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(
                MmsImagePartContract.acceptPart(
                    message,
                    MmsImagePartContract.PartMetadata(
                        partId = id,
                        parentMessageId = 42L,
                        mimeType = "image/png"
                    )
                )
            )
        }
    }
}
