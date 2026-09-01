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
 * The WireGuard backend is deliberately delegated to the audited WireGuard
 * tunnel library. Sentinel does not implement cryptography or packet framing.
 * Configuration is accepted only in memory; private keys are never logged or
 * persisted by this controller.
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
            // State is queried from the backend; do not log configuration or keys.
        }
    }

    /**
     * Returns the Android VPN consent intent, or null when already authorized.
     * The caller must launch this intent from an Activity.
     */
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

        val config = try {
            ByteArrayInputStream(bytes).use { Config.parse(it) }
        } catch (e: BadConfigException) {
            throw IllegalArgumentException("Invalid WireGuard configuration", e)
        }

        // Sentinel is a defensive full-device VPN. A profile without a default
        // route can silently fall back to the underlying network and defeat the
        // protection objective. Require an explicit full-tunnel route.
        val hasFullTunnelRoute = Regex(
            "(?im)^\\s*AllowedIPs\\s*=.*(?:0\\.0\\.0\\.0/0|::/0)"
        ).containsMatchIn(configText)
        require(hasFullTunnelRoute) {
            "Sentinel VPN requires an explicit full-tunnel AllowedIPs route"
        }

        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    /** Disconnects the Sentinel VPN. Must run off the main thread. */
    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP
}
