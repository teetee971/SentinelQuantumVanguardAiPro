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
        val status: GatewayStatus
    ) {
        init {
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{1,62}"))) { "Invalid gateway id" }
            require(countryCode.matches(Regex("[A-Z]{2}"))) { "Invalid country code" }
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

        if (prepareConsentIntent() != null) {
            runtimeState = RuntimeState.CONSENT_REQUIRED
            configuration.fill(0)
            return OperationResult(runtimeState, "ANDROID_VPN_CONSENT_REQUIRED")
        }

        val parsedConfig = try {
            parseAndValidateFullTunnelConfig(configuration)
        } catch (_: Exception) {
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
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_UP")
            }
        } catch (_: Exception) {
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_START_FAILED")
        }
    }

    suspend fun disconnect(): OperationResult = operationMutex.withLock {
        return try {
            val backendState = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            }
            if (backendState == Tunnel.State.DOWN) {
                runtimeState = RuntimeState.DISCONNECTED
                OperationResult(runtimeState, "TUNNEL_DOWN")
            } else {
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_DOWN")
            }
        } catch (_: Exception) {
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_STOP_FAILED")
        }
    }

    companion object {
        private const val TUNNEL_NAME = "sentinel"
        internal const val MAX_CONFIG_BYTES = 64 * 1024

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
            return config
        }
    }
}
