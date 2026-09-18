package com.sentinel.quantum.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketCodecTest {
    @Test fun rejectsNonDnsPackets() {
        assertNull(DnsPacketCodec.parseIpv4UdpDns(ByteArray(20)))
    }

    @Test fun parsesAndBlocksDnsQuery() {
        val query = dnsQuery("ads.doubleclick.net")
        val packet = ipv4Udp(query, 53000, 53)
        val parsed = DnsPacketCodec.parseIpv4UdpDns(packet)
        assertNotNull(parsed)
        assertEquals("ads.doubleclick.net", parsed!!.domain)
        val response = DnsPacketCodec.buildNxDomain(parsed)
        val dnsOffset = 28
        val flags = ((response[dnsOffset + 2].toInt() and 0xff) shl 8) or
            (response[dnsOffset + 3].toInt() and 0xff)
        assertEquals(3, flags and 0xf)
    }

    private fun dnsQuery(domain: String): ByteArray {
        val labels = domain.split('.')
        val question = mutableListOf<Byte>()
        labels.forEach { label ->
            question += label.length.toByte()
            question += label.toByteArray(Charsets.US_ASCII).toList()
        }
        question += 0
        question += listOf(0, 1, 0, 1).map(Int::toByte)
        return ByteArray(12 + question.size).also { dns ->
            dns[0] = 0x12
            dns[1] = 0x34
            dns[2] = 0x01
            dns[5] = 0x01
            question.toByteArray().copyInto(dns, 12)
        }
    }

    private fun ipv4Udp(dns: ByteArray, srcPort: Int, dstPort: Int): ByteArray {
        val total = 20 + 8 + dns.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        packet[2] = (total ushr 8).toByte()
        packet[3] = total.toByte()
        packet[8] = 64
        packet[9] = 17
        byteArrayOf(10, 7, 0, 1).copyInto(packet, 12)
        byteArrayOf(10, 7, 0, 2).copyInto(packet, 16)
        packet[20] = (srcPort ushr 8).toByte()
        packet[21] = srcPort.toByte()
        packet[22] = (dstPort ushr 8).toByte()
        packet[23] = dstPort.toByte()
        val udp = 8 + dns.size
        packet[24] = (udp ushr 8).toByte()
        packet[25] = udp.toByte()
        dns.copyInto(packet, 28)
        return packet
    }
}
