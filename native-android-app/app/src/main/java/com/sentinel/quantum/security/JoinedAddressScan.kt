package com.sentinel.quantum.security

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class ArpNeighbor(val ipv4: String, val mac: String)

data class JoinedHost(
    val ipv4: String,
    val mac: String,
    val vendor: String,
    val appearedAfterQuestion: Boolean
)

data class JoinedAddressOutcome(val hosts: List<JoinedHost>, val note: String)

/** Local address question. One UDP packet per host, then the phone's own neighbor table. No port scan. */
object JoinedAddressPlan {
    fun hosts(ipv4: String, prefix: Int?): List<String> {
        val length = prefix ?: return emptyList()
        if (length !in 24..30) return emptyList()
        val own = parse(ipv4) ?: return emptyList()
        val mask = if (length == 0) 0L else (-1L shl (32 - length)) and 0xffff_ffffL
        val network = own and mask
        val broadcast = network or mask.inv() and 0xffff_ffffL
        val found = mutableListOf<String>()
        var cursor = network + 1
        while (cursor < broadcast && found.size < 254) {
            if (cursor != own) found += format(cursor)
            cursor++
        }
        return found
    }

    fun parseArp(text: String): List<ArpNeighbor> {
        val rows = mutableListOf<ArpNeighbor>()
        text.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 4) return@forEach
            val ip = parts[0]
            val flags = parts[2].removePrefix("0x").toIntOrNull(16) ?: return@forEach
            val mac = parts[3].lowercase()
            if (flags and 0x2 == 0) return@forEach
            if (parse(ip) == null || mac == "00:00:00:00:00:00" || !mac.contains(':')) return@forEach
            rows += ArpNeighbor(ip, mac)
        }
        return rows
    }

    private fun parse(ipv4: String): Long? {
        val parts = ipv4.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    private fun format(value: Long): String =
        "${(value shr 24) and 0xff}.${(value shr 16) and 0xff}.${(value shr 8) and 0xff}.${value and 0xff}"
}

class JoinedAddressScan {
    fun discover(ipv4: String?, prefix: Int?): JoinedAddressOutcome {
        val own = ipv4?.trim().orEmpty()
        if (JoinedAddressPlan.hosts(own, prefix).isEmpty()) {
            return JoinedAddressOutcome(
                emptyList(),
                "Réseau trop grand, ou adresse non lue. Aucune question n’a été envoyée."
            )
        }
        val before = readArp().map { it.ipv4 }.toSet()
        JoinedAddressPlan.hosts(own, prefix).forEach { host ->
            runCatching {
                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(byteArrayOf(0), 1, InetAddress.getByName(host), 9)
                    socket.send(packet)
                }
            }
        }
        Thread.sleep(700)
        val hosts = readArp()
            .filter { it.ipv4 != own }
            .distinctBy { it.mac }
            .map { row ->
                JoinedHost(
                    ipv4 = row.ipv4,
                    mac = row.mac,
                    vendor = WifiRadioAnalyzer.vendorLabel(row.mac),
                    appearedAfterQuestion = row.ipv4 !in before
                )
            }
        return JoinedAddressOutcome(
            hosts,
            "Question locale envoyée. Seules les adresses présentes dans la table du téléphone sont listées. Une caméra qui ne répond pas reste absente."
        )
    }

    private fun readArp(): List<ArpNeighbor> =
        runCatching { JoinedAddressPlan.parseArp(java.io.File("/proc/net/arp").readText()) }.getOrDefault(emptyList())
}
