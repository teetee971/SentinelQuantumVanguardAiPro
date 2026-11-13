package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MmsThumbnailPolicyTest {

    @Test
    fun acceptsValidPartIdentity() {
        assertTrue(
            MmsThumbnailPolicy.validKey(
                MmsThumbnailPolicy.PartKey(42, 7, 3)
            )
        )
    }

    @Test
    fun rejectsInvalidPartIdentities() {
        val ids = listOf(0L, -1L, Long.MIN_VALUE)
        for (id in ids) {
            assertFalse(
                MmsThumbnailPolicy.validKey(
                    MmsThumbnailPolicy.PartKey(id, 7, 3)
                )
            )
            assertFalse(
                MmsThumbnailPolicy.validKey(
                    MmsThumbnailPolicy.PartKey(42, id, 3)
                )
            )
            assertFalse(
                MmsThumbnailPolicy.validKey(
                    MmsThumbnailPolicy.PartKey(42, 7, id)
                )
            )
        }
    }

    @Test
    fun rejectsInvalidSourceDimensions() {
        assertFalse(MmsThumbnailPolicy.validSource(0, 100))
        assertFalse(MmsThumbnailPolicy.validSource(100, -1))
        assertFalse(MmsThumbnailPolicy.validSource(16385, 100))
        assertFalse(MmsThumbnailPolicy.validSource(5000, 5000))
        assertFalse(
            MmsThumbnailPolicy.validSource(
                Int.MAX_VALUE,
                Int.MAX_VALUE
            )
        )
    }

    @Test
    fun acceptsSourceAtPixelLimit() {
        assertTrue(
            MmsThumbnailPolicy.validSource(5000, 4000)
        )
    }

    @Test
    fun calculatesBoundedSampleSize() {
        assertEquals(
            4,
            MmsThumbnailPolicy.sampleSize(2048, 1536, 512)
        )
        assertEquals(
            1,
            MmsThumbnailPolicy.sampleSize(320, 240, 512)
        )
    }

    @Test
    fun rejectsInvalidTargetSize() {
        assertNull(
            MmsThumbnailPolicy.sampleSize(800, 600, 0)
        )
        assertNull(
            MmsThumbnailPolicy.sampleSize(800, 600, 2048)
        )
    }

    @Test
    fun keepsDecodedThumbnailWithinPixelBudget() {
        val sample = MmsThumbnailPolicy.sampleSize(
            5000, 4000, 1024
        )
        assertNotNull(sample)

        val width = 5000 / sample!!
        val height = 4000 / sample

        assertTrue(
            width.toLong() * height.toLong() <=
                MmsThumbnailPolicy.MAX_THUMBNAIL_PIXELS
        )
    }
}
