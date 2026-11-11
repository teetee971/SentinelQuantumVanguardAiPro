package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinedAddressPlanTest {
    @Test
    fun aHomePrefixIsQuestionedAndALargeOneIsNot() {
        val hosts = JoinedAddressPlan.hosts("192.168.1.20", 24)
        assertEquals(253, hosts.size)
        assertTrue("192.168.1.1" in hosts)
        assertTrue("192.168.1.20" !in hosts)
        assertTrue(JoinedAddressPlan.hosts("10.0.0.2", 16).isEmpty())
    }

    @Test
    fun onlyCompleteNeighborsAreKept() {
        val text = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         2C:93:FB:00:00:01     *        wlan0
            192.168.1.50     0x1         0x0         00:00:00:00:00:00     *        wlan0
        """.trimIndent()
        val rows = JoinedAddressPlan.parseArp(text)
        assertEquals(1, rows.size)
        assertEquals("2c:93:fb:00:00:01", rows[0].mac)
    }
}
