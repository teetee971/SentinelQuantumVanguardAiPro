package com.sentinel.quantum.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import java.net.Inet4Address

/**
 * Identity of the Wi-Fi the phone has actually joined.
 * Neighboring networks do not expose their clients, gateway or addresses.
 */
data class WifiJoinedLink(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val linkSpeedMbps: Int,
    val rxLinkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val frequencyMhz: Int,
    val ipv4: String?,
    val prefixLength: Int?,
    val gateway: String?,
    val dns: List<String>
)

object WifiJoinedLinkReader {
    fun read(context: Context): WifiJoinedLink? {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val wifi = wifiInfo(context, capabilities) ?: return null
        val links = connectivity.getLinkProperties(network)
        val ipv4 = links?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val ssid = wifi.ssid?.trim('"')?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", true) }
            ?: WifiRiskEvaluator.HIDDEN_SSID_LABEL
        return WifiJoinedLink(
            ssid = ssid,
            bssid = wifi.bssid.orEmpty(),
            rssiDbm = wifi.rssi,
            linkSpeedMbps = wifi.linkSpeed,
            rxLinkSpeedMbps = directedSpeed(wifi, receive = true),
            txLinkSpeedMbps = directedSpeed(wifi, receive = false),
            frequencyMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifi.frequency else 0,
            ipv4 = (ipv4?.address as? Inet4Address)?.hostAddress,
            prefixLength = ipv4?.prefixLength?.takeIf { it in 0..32 },
            gateway = defaultGateway(links),
            dns = links?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        )
    }

    private fun wifiInfo(context: Context, capabilities: NetworkCapabilities): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return capabilities.transportInfo as? WifiInfo
        }
        @Suppress("DEPRECATION")
        return context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.connectionInfo
    }

    private fun defaultGateway(links: LinkProperties?): String? {
        val routes = links?.routes.orEmpty()
        val gateway = routes.firstOrNull { it.isDefaultRoute }?.gateway
            ?: routes.firstOrNull { it.gateway != null }?.gateway
        return gateway?.hostAddress
    }

    private fun directedSpeed(wifi: WifiInfo, receive: Boolean): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val speed = if (receive) wifi.rxLinkSpeedMbps else wifi.txLinkSpeedMbps
        return speed.takeIf { it > 0 }
    }
}
