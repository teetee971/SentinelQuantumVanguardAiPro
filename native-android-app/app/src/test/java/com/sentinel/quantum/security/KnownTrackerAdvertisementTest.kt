package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownTrackerAdvertisementTest {
    @Test
    fun tileChipoloAndSamsungMatchThePublishedFilters() {
        val samsung = "0000FD5A-0000-1000-8000-00805F9B34FB"
        val tile = "0000FEED-0000-1000-8000-00805F9B34FB"
        val chipolo = "0000FE33-0000-1000-8000-00805F9B34FB"
        assertEquals("samsung", KnownTrackerAdvertisements.match(emptyList(), mapOf(samsung to byteArrayOf(0x10, 1, 2, 3)))?.kind)
        assertNull(KnownTrackerAdvertisements.match(emptyList(), mapOf(samsung to byteArrayOf(0x18))))
        assertEquals("tile", KnownTrackerAdvertisements.match(emptyList(), mapOf(tile to byteArrayOf(0x02, 0x00, 1, 2)))?.kind)
        assertNull(KnownTrackerAdvertisements.match(emptyList(), mapOf(tile to byteArrayOf(0x02, 0x01))))
        assertEquals("chipolo", KnownTrackerAdvertisements.match(listOf(chipolo), emptyMap())?.kind)
        assertEquals(
            "pebblebee",
            KnownTrackerAdvertisements.match(listOf("0000FA25-0000-1000-8000-00805F9B34FB"), emptyMap())?.kind
        )
        assertNull(KnownTrackerAdvertisements.match(listOf("0000FEAA-0000-1000-8000-00805F9B34FB"), emptyMap()))
    }
}
