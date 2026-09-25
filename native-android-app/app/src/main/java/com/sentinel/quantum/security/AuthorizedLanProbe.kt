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

    fun validate(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext,
        targets: List<AuthorizedProbeTarget>
    ): List<AuthorizedProbeTarget>? {
        if (!NetworkOperationPolicy.permitsActiveProbe(mode, authorization)) return null
        if (targets.isEmpty() || targets.size > MAX_TARGETS) return null
        val normalized = targets.map { target ->
            val host = target.host.trim()
            if (host.isEmpty() || target.ports.isEmpty() || target.ports.size > MAX_PORTS_PER_TARGET) return null
            if (target.ports.any { it !in 1..65535 }) return null
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            if (!isLocalAddress(address)) return null
            target.copy(host = address.hostAddress ?: return null, ports = target.ports.distinct())
        }
        return normalized
    }

    private fun isLocalAddress(address: InetAddress): Boolean =
        !address.isAnyLocalAddress && (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress)
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
            target.ports.map { port ->
                val reachable = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(target.host, port), AuthorizedLanProbePolicy.CONNECT_TIMEOUT_MS)
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
