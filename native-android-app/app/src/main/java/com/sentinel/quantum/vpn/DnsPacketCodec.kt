package com.sentinel.quantum.vpn

import java.net.InetAddress

object DnsPacketCodec {
    data class Query(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val dnsPayload: ByteArray,
        val domain: String,
        val questionEnd: Int
    )

    fun parseIpv4UdpDns(packet: ByteArray, length: Int = packet.size): Query? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if ((packet[9].toInt() and 0xff) != 17) return null
        val totalLength = u16(packet, 2).coerceAtMost(length)
        if (totalLength < ihl + 8) return null
        val sourcePort = u16(packet, ihl)
        val destinationPort = u16(packet, ihl + 2)
        if (destinationPort != 53) return null
        val udpLength = u16(packet, ihl + 4)
        if (udpLength < 8 || ihl + udpLength > totalLength) return null
        val dns = packet.copyOfRange(ihl + 8, ihl + udpLength)
        if (dns.size < 17 || u16(dns, 4) < 1) return null
        val parsed = parseQuestionName(dns, 12) ?: return null
        return Query(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            dnsPayload = dns,
            domain = parsed.first,
            questionEnd = parsed.second
        )
    }

    fun buildNxDomain(query: Query): ByteArray {
        val end = query.questionEnd.coerceIn(12, query.dnsPayload.size)
        val dns = query.dnsPayload.copyOfRange(0, end)
        val requestFlags = u16(query.dnsPayload, 2)
        val responseFlags = 0x8000 or (requestFlags and 0x0100) or 0x0080 or 0x0003
        putU16(dns, 2, responseFlags)
        putU16(dns, 6, 0)
        putU16(dns, 8, 0)
        putU16(dns, 10, 0)
        return buildIpv4UdpResponse(query, dns)
    }

    fun buildIpv4UdpResponse(query: Query, dnsPayload: ByteArray): ByteArray {
        require(dnsPayload.size <= 4096) { "DNS payload too large" }
        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        packet[1] = 0
        putU16(packet, 2, totalLength)
        putU16(packet, 4, 0)
        putU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 17
        query.destinationAddress.copyInto(packet, 12)
        query.sourceAddress.copyInto(packet, 16)
        putU16(packet, 10, checksum(packet, 0, 20))
        putU16(packet, 20, query.destinationPort)
        putU16(packet, 22, query.sourcePort)
        putU16(packet, 24, udpLength)
        putU16(packet, 26, 0)
        dnsPayload.copyInto(packet, 28)
        val udpChecksum = udpChecksum(packet, 12, 16, 20, udpLength)
        putU16(packet, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        return packet
    }

    private fun parseQuestionName(dns: ByteArray, start: Int): Pair<String, Int>? {
        var offset = start
        val labels = mutableListOf<String>()
        var totalChars = 0
        while (offset < dns.size) {
            val size = dns[offset].toInt() and 0xff
            offset += 1
            if (size == 0) break
            if ((size and 0xc0) != 0 || size > 63 || offset + size > dns.size) return null
            val label = dns.copyOfRange(offset, offset + size).toString(Charsets.US_ASCII)
            if (label.any { !(it.isLetterOrDigit() || it == '-') }) return null
            labels += label
            totalChars += label.length + 1
            if (totalChars > 253) return null
            offset += size
        }
        if (labels.isEmpty() || offset + 4 > dns.size) return null
        return labels.joinToString(".").lowercase() to (offset + 4)
    }

    private fun udpChecksum(packet: ByteArray, srcOffset: Int, dstOffset: Int, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        fun addWord(high: Int, low: Int) {
            sum += ((high and 0xff) shl 8) or (low and 0xff)
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        }
        for (i in 0 until 4 step 2) addWord(packet[srcOffset + i].toInt(), packet[srcOffset + i + 1].toInt())
        for (i in 0 until 4 step 2) addWord(packet[dstOffset + i].toInt(), packet[dstOffset + i + 1].toInt())
        addWord(0, 17)
        addWord((udpLength ushr 8) and 0xff, udpLength and 0xff)
        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            addWord(packet[i].toInt(), packet[i + 1].toInt())
            i += 2
        }
        if (i < end) addWord(packet[i].toInt(), 0)
        return sum.inv().toInt() and 0xffff
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
            i += 2
        }
        if (i < end) sum += (bytes[i].toInt() and 0xff) shl 8
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 1] = (value and 0xff).toByte()
    }
}
