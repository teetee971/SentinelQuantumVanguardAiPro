package com.sentinel.security

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Owns Sentinel's defensive VPN lifecycle.
 *
 * The WireGuard backend is delegated to the WireGuard tunnel library. Sentinel
 * does not implement cryptography or packet framing. Configuration is accepted
 * only in memory; private keys are never logged or persisted by this controller.
 */
class SentinelVpnController(context: Context) {
    companion object {
        private const val MAX_CONFIG_BYTES = 64 * 1024
        private const val TUNNEL_NAME = "sentinel"
    }

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            // State is queried from the backend; never log configuration or keys.
        }
    }

    /** Returns the Android VPN consent intent, or null when already authorized. */
    fun prepareIntent(): Intent? = VpnService.prepare(appContext)

    /**
     * Validates and connects a WireGuard configuration.
     * This method performs blocking tunnel setup and must run off the main thread.
     */
    @Throws(Exception::class)
    fun connect(configText: String) {
        val bytes = configText.toByteArray(StandardCharsets.UTF_8)
        require(bytes.isNotEmpty()) { "VPN configuration is empty" }
        require(bytes.size <= MAX_CONFIG_BYTES) { "VPN configuration is too large" }
        require(VpnService.prepare(appContext) == null) {
            "VPN authorization is required before Sentinel can connect"
        }

        val config = try {
            ByteArrayInputStream(bytes).use { Config.parse(it) }
        } catch (e: BadConfigException) {
            throw IllegalArgumentException("Invalid WireGuard configuration", e)
        }

        // Full-device protection requires both address families. Requiring only
        // one default route would permit the other family to bypass the tunnel.
        val hasIpv4DefaultRoute = Regex("(?im)^\\s*AllowedIPs\\s*=.*(?:^|[,\\s])0\\.0\\.0\\.0/0(?:[,\\s]|$)")
            .containsMatchIn(configText)
        val hasIpv6DefaultRoute = Regex("(?im)^\\s*AllowedIPs\\s*=.*(?:^|[,\\s])::/0(?:[,\\s]|$)")
            .containsMatchIn(configText)
        require(hasIpv4DefaultRoute && hasIpv6DefaultRoute) {
            "Sentinel VPN requires explicit IPv4 and IPv6 full-tunnel AllowedIPs routes"
        }

        backend.setState(tunnel, Tunnel.State.UP, config)
        check(isConnected()) { "Sentinel VPN failed to enter the UP state" }
    }

    /** Disconnects the Sentinel VPN. Must run off the main thread. */
    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP
}
