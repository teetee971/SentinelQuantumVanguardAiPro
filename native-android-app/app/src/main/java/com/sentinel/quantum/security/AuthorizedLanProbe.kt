package com.sentinel.quantum.security

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class AuthorizedProbeTarget(val host: String, val ports: List<Int>)
data class AuthorizedProbeResult(val host: String, val port: Int, val reachable: Boolean)

object AuthorizedLanProbePolicy {
    const val MAX_TARGETS = 32
    const val MAX_PORTS_PER_TARGET = 16
    const val CONNECT_TIMEOUT_MS = 500
    const val MAX_CONNECTIONS_PER_RUN = 32
    const val MAX_HOST_LENGTH = 64

    fun validate(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext,
        targets: List<AuthorizedProbeTarget>
    ): List<AuthorizedProbeTarget>? {
        if (!NetworkOperationPolicy.permitsActiveProbe(mode, authorization)) return null
        if (targets.isEmpty() || targets.size > MAX_TARGETS) return null
        var connectionCount = 0
        val normalized = targets.map { target ->
            // Reject controls and oversized inputs before any normalization or parsing.
            if (target.host.length > MAX_HOST_LENGTH || target.host.any { it.isISOControl() }) return null
            val host = target.host.trim()
            if (host.isEmpty() || target.ports.isEmpty() || target.ports.size > MAX_PORTS_PER_TARGET) return null
            if (target.ports.any { it !in 1..65535 }) return null
            val address = parseNumericAddress(host) ?: return null
            if (!isLocalAddress(address)) return null
            val uniquePorts = target.ports.distinct()
            connectionCount += uniquePorts.size
            if (connectionCount > MAX_CONNECTIONS_PER_RUN) return null
            target.copy(host = address.hostAddress ?: return null, ports = uniquePorts)
        }
        return normalized
    }

    /** Numeric IPv4/IPv6 parser; never invokes hostname resolution. */
    internal fun parseNumericAddress(host: String): InetAddress? {
        if (!host.contains(':')) {
            val octets = host.split('.')
            if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 ||
                    (it.length > 1 && it[0] == '0') || it.any { char -> char !in '0'..'9' } }) return null
            val values = octets.map { it.toIntOrNull() ?: return null }
            if (values.any { it > 255 }) return null
            return InetAddress.getByAddress(values.map { it.toByte() }.toByteArray())
        }

        // Reject scope identifiers, bracket notation and IPv4-mixed forms.
        if (host.any { it !in "0123456789abcdefABCDEF:" }) return null
        val halves = host.split("::", limit = 3)
        if (halves.size > 2) return null
        fun groups(part: String): List<String>? {
            if (part.isEmpty()) return emptyList()
            val tokens = part.split(':')
            if (tokens.any { it.isEmpty() || it.length > 4 || it.any { c -> c !in "0123456789abcdefABCDEF" } }) return null
            return tokens
        }
        val left = groups(halves[0]) ?: return null
        val right = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        val count = left.size + right.size
        if (halves.size == 2 && count >= 8) return null
        if (halves.size == 1 && count != 8) return null
        val words = left + List(if (halves.size == 2) 8 - count else 0) { "0" } + right
        if (words.size != 8) return null
        val bytes = ByteArray(16)
        words.forEachIndexed { index, word ->
            val value = word.toInt(16)
            bytes[index * 2] = (value ushr 8).toByte()
            bytes[index * 2 + 1] = value.toByte()
        }
        // Do not reinterpret an IPv4-mapped IPv6 literal as IPv4.
        if (bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()) return null
        return InetAddress.getByAddress(bytes)
    }

    private fun isLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address
        val ipv6UniqueLocal = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        return !address.isAnyLocalAddress &&
            (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || ipv6UniqueLocal)
    }
}

/**
 * Bounded TCP connect verification for an explicit ADVANCED_LAB session on owned/administered
 * local equipment. It does not send protocol payloads, capture traffic, exploit services,
 * bypass authentication, or target Internet hosts.
 */
object AuthorizedLanSocketProbe {
    fun run(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext,
        targets: List<AuthorizedProbeTarget>
    ): List<AuthorizedProbeResult>? {
        val accepted = AuthorizedLanProbePolicy.validate(mode, authorization, targets) ?: return null
        return accepted.flatMap { target ->
            val address = AuthorizedLanProbePolicy.parseNumericAddress(target.host) ?: return null
            target.ports.map { port ->
                val reachable = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, port), AuthorizedLanProbePolicy.CONNECT_TIMEOUT_MS)
                    }
                    true
                }.getOrDefault(false)
                AuthorizedProbeResult(target.host, port, reachable)
            }
        }
    }
}

enum class AuthorizedTrafficSource { THIS_DEVICE, ADMINISTERED_MIRROR }

data class AuthorizedPacketCaptureSession(
    val source: AuthorizedTrafficSource,
    val durationSeconds: Int,
    val maxBytes: Long,
    val decryptEncryptedPayloads: Boolean = false
)

/**
 * Policy boundary for a future packet collector. Capture is allowed only during an explicit
 * authorized ADVANCED_LAB session, is bounded, and never authorizes TLS decryption.
 */
object AuthorizedPacketCapturePolicy {
    const val MAX_DURATION_SECONDS = 300
    const val MAX_CAPTURE_BYTES = 16L * 1024L * 1024L

    fun permits(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext,
        session: AuthorizedPacketCaptureSession
    ): Boolean =
        NetworkOperationPolicy.permitsTrafficInspection(mode, authorization) &&
            session.durationSeconds in 1..MAX_DURATION_SECONDS &&
            session.maxBytes in 1..MAX_CAPTURE_BYTES &&
            !session.decryptEncryptedPayloads
}

enum class AuthorizedLabSimulation { TCP_CONNECT_VERIFY, SERVICE_AVAILABILITY_RECHECK }

/** Non-exploitative adversarial-lab actions. No credential attacks, injection, DoS or bypass. */
object AuthorizedLabSimulationPolicy {
    fun permits(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext,
        simulation: AuthorizedLabSimulation
    ): Boolean = NetworkOperationPolicy.permitsAdversarialSimulation(mode, authorization)
}
