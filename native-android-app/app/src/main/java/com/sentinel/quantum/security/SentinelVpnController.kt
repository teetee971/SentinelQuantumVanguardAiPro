package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import java.io.ByteArrayInputStream
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sentinel-owned orchestration around the maintained WireGuard Android backend.
 *
 * This controller never provisions a gateway and never invents one. A caller may only connect to
 * a gateway already proven AVAILABLE by the signed Sentinel gateway catalog/control plane.
 * Configuration is accepted as a bounded byte array so the transport buffer can be zeroed after
 * parsing; private keys must never be logged.
 */
class SentinelVpnController(
    context: Context,
    private val backend: Backend = GoBackend(context.applicationContext)
) {
    enum class RuntimeState {
        READY_NO_GATEWAY,
        CONSENT_REQUIRED,
        CONNECTING,
        PROTECTED,
        DEGRADED,
        FAILED,
        DISCONNECTED
    }

    enum class GatewayStatus {
        PLANNED,
        PROVISIONING,
        DEGRADED,
        AVAILABLE,
        DRAINING,
        OFFLINE,
        REVOKED
    }

    data class GatewayDescriptor(
        val id: String,
        val countryCode: String,
        val status: GatewayStatus,
        val dnsServerAddresses: Set<String>
    ) {
        init {
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{1,62}"))) { "Invalid gateway id" }
            require(countryCode.matches(Regex("[A-Z]{2}"))) { "Invalid country code" }
            require(dnsServerAddresses.size <= MAX_GATEWAY_DNS_SERVERS) { "Too many gateway DNS servers" }
            dnsServerAddresses.forEach { canonicalizeNumericIp(it) }
            if (status == GatewayStatus.AVAILABLE) {
                require(dnsServerAddresses.isNotEmpty()) { "AVAILABLE gateway requires pinned DNS servers" }
            }
        }
    }

    data class OperationResult(
        val state: RuntimeState,
        val reason: String
    )

    private val appContext = context.applicationContext
    private val operationMutex = Mutex()

    @Volatile
    private var runtimeState: RuntimeState = RuntimeState.DISCONNECTED

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            runtimeState = when (newState) {
                Tunnel.State.UP -> RuntimeState.PROTECTED
                Tunnel.State.DOWN -> RuntimeState.DISCONNECTED
                Tunnel.State.TOGGLE -> runtimeState
            }
        }
    }

    fun currentState(): RuntimeState = runtimeState

    /** Returns the Android consent intent, or null if the user has already granted VPN consent. */
    fun prepareConsentIntent(): Intent? = VpnService.prepare(appContext)

    suspend fun connect(
        gateway: GatewayDescriptor,
        configuration: ByteArray
    ): OperationResult = operationMutex.withLock {
        if (gateway.status != GatewayStatus.AVAILABLE) {
            runtimeState = RuntimeState.READY_NO_GATEWAY
            configuration.fill(0)
            return OperationResult(runtimeState, "GATEWAY_NOT_AVAILABLE")
        }

        if (!SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.INTERNET_VPN)) {
            runtimeState = RuntimeState.FAILED
            configuration.fill(0)
            return OperationResult(runtimeState, "PRIVATE_MESH_ALREADY_ACTIVE")
        }

        if (prepareConsentIntent() != null) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
            runtimeState = RuntimeState.CONSENT_REQUIRED
            configuration.fill(0)
            return OperationResult(runtimeState, "ANDROID_VPN_CONSENT_REQUIRED")
        }

        val parsedConfig = try {
            parseAndValidateFullTunnelConfig(configuration).also {
                validateGatewayDns(it, gateway)
            }
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
            runtimeState = RuntimeState.FAILED
            return OperationResult(runtimeState, "INVALID_OR_UNSAFE_CONFIGURATION")
        } finally {
            configuration.fill(0)
        }

        runtimeState = RuntimeState.CONNECTING
        return try {
            val backendState = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.UP, parsedConfig)
            }
            if (backendState == Tunnel.State.UP) {
                runtimeState = RuntimeState.PROTECTED
                OperationResult(runtimeState, "TUNNEL_UP")
            } else {
                SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_UP")
            }
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_START_FAILED")
        }
    }

    suspend fun disconnect(): OperationResult = operationMutex.withLock {
        return try {
            val backendState = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            }
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
            if (backendState == Tunnel.State.DOWN) {
                runtimeState = RuntimeState.DISCONNECTED
                OperationResult(runtimeState, "TUNNEL_DOWN")
            } else {
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_DOWN")
            }
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.INTERNET_VPN)
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_STOP_FAILED")
        }
    }

    companion object {
        private const val TUNNEL_NAME = "sentinel"
        internal const val MAX_CONFIG_BYTES = 64 * 1024
        internal const val MAX_GATEWAY_DNS_SERVERS = 4

        /**
         * Parse a WireGuard configuration and enforce Sentinel's defensive full-tunnel invariant.
         * Both IPv4 and IPv6 default routes are required to avoid an accidental protocol-family leak.
         */
        internal fun parseAndValidateFullTunnelConfig(configuration: ByteArray): Config {
            require(configuration.isNotEmpty()) { "Empty configuration" }
            require(configuration.size <= MAX_CONFIG_BYTES) { "Configuration too large" }
            require(configuration.none { it == 0.toByte() }) { "NUL byte forbidden" }

            val config = ByteArrayInputStream(configuration).use { Config.parse(it) }
            require(config.peers.isNotEmpty()) { "At least one peer is required" }
            require(config.peers.any { it.endpoint.isPresent }) { "A peer endpoint is required" }

            val allowedIps = config.peers
                .flatMap { peer -> peer.allowedIps }
                .toSet()
            val ipv4Default = InetNetwork.parse("0.0.0.0/0")
            val ipv6Default = InetNetwork.parse("::/0")

            require(ipv4Default in allowedIps) { "IPv4 default route required" }
            require(ipv6Default in allowedIps) { "IPv6 default route required" }
            require(config.getInterface().getDnsServers().isNotEmpty()) {
                "At least one tunnel DNS server is required"
            }
            return config
        }

        internal fun validateGatewayDns(config: Config, gateway: GatewayDescriptor) {
            require(gateway.status == GatewayStatus.AVAILABLE) {
                "Gateway must be AVAILABLE before DNS validation"
            }
            val expected = gateway.dnsServerAddresses
                .map(::canonicalizeNumericIp)
                .toSet()
            require(expected.isNotEmpty()) { "Gateway DNS pin set is empty" }

            val configured = config.getInterface().getDnsServers()
                .map { it.hostAddress.substringBefore('%').lowercase() }
                .toSet()
            require(configured.isNotEmpty()) { "Tunnel DNS set is empty" }
            require(configured.all { it in expected }) {
                "Tunnel DNS contains an address not pinned to the gateway"
            }
        }

        internal fun canonicalizeNumericIp(raw: String): String {
            val value = raw.trim()
            require(value == raw && value.length in 2..45) { "Invalid DNS address" }

            val ipv4Literal = value.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))
            val ipv6Literal = value.contains(':') &&
                value.matches(Regex("""[0-9A-Fa-f:.]+"""))
            require(ipv4Literal || ipv6Literal) { "DNS address must be a numeric IP literal" }

            val parsed = InetAddress.getByName(value)
            val canonical = parsed.hostAddress.substringBefore('%').lowercase()
            if (ipv4Literal) {
                require(parsed.address.size == 4) { "Invalid IPv4 DNS address" }
            } else {
                require(parsed.address.size == 16) { "Invalid IPv6 DNS address" }
            }
            return canonical
        }
    }
}
