package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MmsPartProviderContractTest {
    private val identity = MmsImagePartContract.MessageIdentity(-42L, 42L, 7L)
    private val key = MmsThumbnailPolicy.PartKey(42L, 7L, 11L)
    private val metadata = MmsImagePartContract.PartMetadata(11L, 42L, "image/jpeg")

    @Test fun itemUriUsesTheProviderGlobalPartRoute() {
        assertEquals("content://mms/part/11", MmsImagePartContract.partContentUri(key))
        assertEquals("content://mms/part/11", MmsImagePartContract.partContentUri(key.copy(providerMessageId = 84L)))
    }

    @Test fun invalidMessageThreadOrPartCannotProduceAnItemUri() {
        for (invalid in listOf(key.copy(providerMessageId = 0L), key.copy(threadId = 0L), key.copy(partId = 0L), key.copy(partId = -1L))) {
            assertNull(MmsImagePartContract.partContentUri(invalid))
        }
    }

    @Test fun unchangedValidatedPartCanBeReleased() {
        assertTrue(MmsImagePartContract.samePartSnapshot(identity, metadata, metadata))
    }

    @Test fun disappearanceOrReassignmentAfterReadIsRejected() {
        assertFalse(MmsImagePartContract.samePartSnapshot(identity, metadata, null))
        assertFalse(MmsImagePartContract.samePartSnapshot(identity, metadata, metadata.copy(partId = 12L)))
        assertFalse(MmsImagePartContract.samePartSnapshot(identity, metadata, metadata.copy(parentMessageId = 84L)))
    }

    @Test fun mimeChangedDuringProviderReadIsRejectedEvenWhenBothTypesAreSupported() {
        assertFalse(MmsImagePartContract.samePartSnapshot(identity, metadata, metadata.copy(mimeType = "image/png")))
    }

    @Test fun equivalentMimeFormattingDoesNotRejectAnOtherwiseStablePart() {
        assertTrue(MmsImagePartContract.samePartSnapshot(identity, metadata, metadata.copy(mimeType = " IMAGE/JPEG ")))
    }
}
