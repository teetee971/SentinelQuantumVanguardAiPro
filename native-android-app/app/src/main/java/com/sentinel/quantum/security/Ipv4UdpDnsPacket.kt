package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal IPv4/UDP DNS packet codec for the local DNS-only VPN. */
data class Ipv4UdpDnsPacket(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsPayload: ByteArray,
    val ipIdentification: Int
) {
    fun queryName(): String? {
        if (dnsPayload.size < 17) return null
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < dnsPayload.size) {
            val length = dnsPayload[offset].toInt() and 0xff
            offset += 1
            if (length == 0) break
            if (length > 63 || offset + length > dnsPayload.size) return null
            labels += dnsPayload.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
            offset += length
            if (labels.size > 32) return null
        }
        if (labels.isEmpty()) return null
        return DnsFilterPolicy.normalizeDomain(labels.joinToString("."))
    }

    fun buildResponse(responseDnsPayload: ByteArray): ByteArray {
        val totalLength = IPV4_HEADER_SIZE + UDP_HEADER_SIZE + responseDnsPayload.size
        require(totalLength <= 65535)
        val packet = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)
        packet.put(0x45.toByte())
        packet.put(0)
        packet.putShort(totalLength.toShort())
        packet.putShort(ipIdentification.toShort())
        packet.putShort(0)
        packet.put(64.toByte())
        packet.put(17.toByte())
        packet.putShort(0)
        packet.put(destinationAddress)
        packet.put(sourceAddress)
        packet.putShort(destinationPort.toShort())
        packet.putShort(sourcePort.toShort())
        packet.putShort((UDP_HEADER_SIZE + responseDnsPayload.size).toShort())
        packet.putShort(0) // UDP checksum is optional for IPv4.
        packet.put(responseDnsPayload)
        val raw = packet.array()
        val checksum = ipv4Checksum(raw, 0, IPV4_HEADER_SIZE)
        raw[10] = (checksum ushr 8).toByte()
        raw[11] = checksum.toByte()
        return raw
    }

    fun buildNxDomain(): ByteArray {
        if (dnsPayload.size < 12) return buildResponse(dnsPayload)
        val response = dnsPayload.copyOf()
        response[2] = ((response[2].toInt() and 0x01) or 0x80).toByte()
        response[3] = 0x83.toByte()
        for (index in 6..11) response[index] = 0
        return buildResponse(response)
    }

    companion object {
        private const val IPV4_HEADER_SIZE = 20
        private const val UDP_HEADER_SIZE = 8

        fun parse(packet: ByteArray, length: Int): Ipv4UdpDnsPacket? {
            if (length < IPV4_HEADER_SIZE + UDP_HEADER_SIZE) return null
            val version = (packet[0].toInt() ushr 4) and 0x0f
            val ihl = (packet[0].toInt() and 0x0f) * 4
            if (version != 4 || ihl < IPV4_HEADER_SIZE || length < ihl + UDP_HEADER_SIZE) return null
            if ((packet[9].toInt() and 0xff) != 17) return null
            val totalLength = ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff)
            if (totalLength > length || totalLength < ihl + UDP_HEADER_SIZE) return null
            val source = packet.copyOfRange(12, 16)
            val destination = packet.copyOfRange(16, 20)
            val udpOffset = ihl
            val sourcePort = ((packet[udpOffset].toInt() and 0xff) shl 8) or (packet[udpOffset + 1].toInt() and 0xff)
            val destinationPort = ((packet[udpOffset + 2].toInt() and 0xff) shl 8) or (packet[udpOffset + 3].toInt() and 0xff)
            val udpLength = ((packet[udpOffset + 4].toInt() and 0xff) shl 8) or (packet[udpOffset + 5].toInt() and 0xff)
            if (udpLength < UDP_HEADER_SIZE || udpOffset + udpLength > totalLength) return null
            val dnsPayload = packet.copyOfRange(udpOffset + UDP_HEADER_SIZE, udpOffset + udpLength)
            val identification = ((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff)
            return Ipv4UdpDnsPacket(source, destination, sourcePort, destinationPort, dnsPayload, identification)
        }

        private fun ipv4Checksum(bytes: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var index = offset
            while (index < offset + length) {
                val high = bytes[index].toInt() and 0xff
                val low = if (index + 1 < offset + length) bytes[index + 1].toInt() and 0xff else 0
                sum += ((high shl 8) or low).toLong()
                while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
                index += 2
            }
            return sum.inv().toInt() and 0xffff
        }
    }
}
