package com.sentinel.quantum.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.InetAddress
import java.net.NetworkInterface

data class NetworkSecurityResult(
    val connectionType: String,
    val isVpnActive: Boolean,
    val isWifiSecure: Boolean?,
    val wifiSecurityType: String?,
    val wifiSSID: String?,
    val proxyEnabled: Boolean,
    val dnsServers: List<String>,
    val localIpAddress: String?,
    val networkInterfaces: List<NetworkInterfaceInfo>
)

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val addresses: List<String>
)

/**
 * NetworkSecurityModule - Real network security audit
 * 
 * Analyzes current network configuration for security risks:
 * - Connection type (WiFi, Mobile, VPN)
 * - WiFi security (WPA2/WPA3 vs. open/WEP)
 * - VPN detection
 * - DNS configuration
 * - Network interfaces
 * 
 * 100% local analysis, no external calls
 * Privacy-preserving: no data leaves device
 */
class NetworkSecurityModule(private val context: Context) {

    fun runAudit(): NetworkSecurityResult {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        return NetworkSecurityResult(
            connectionType = getConnectionType(connectivityManager),
            isVpnActive = isVpnActive(connectivityManager),
            isWifiSecure = if (isWifiConnected(connectivityManager)) isWifiSecure(wifiManager) else null,
            wifiSecurityType = if (isWifiConnected(connectivityManager)) getWifiSecurityType(wifiManager) else null,
            wifiSSID = if (isWifiConnected(connectivityManager)) getWifiSSID(wifiManager) else null,
            proxyEnabled = isProxyEnabled(),
            dnsServers = getDnsServers(),
            localIpAddress = getLocalIpAddress(),
            networkInterfaces = getNetworkInterfaces()
        )
    }

    private fun getConnectionType(cm: ConnectivityManager): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return "None"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "Unknown"
            
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = cm.activeNetworkInfo
            when (activeNetwork?.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "Mobile"
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                ConnectivityManager.TYPE_VPN -> "VPN"
                else -> "Unknown"
            }
        }
    }

    private fun isVpnActive(cm: ConnectivityManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            networks.any { network ->
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }
    }

    private fun isWifiConnected(cm: ConnectivityManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun isWifiSecure(wifiManager: WifiManager): Boolean {
        val wifiInfo = getWifiInfo(wifiManager) ?: return false
        val securityType = getWifiSecurityType(wifiManager)
        
        // Consider WPA2/WPA3 as secure
        return securityType?.contains("WPA") == true && !securityType.contains("WEP")
    }

    private fun getWifiSecurityType(wifiManager: WifiManager): String? {
        try {
            val wifiInfo = getWifiInfo(wifiManager) ?: return null
            val configurations = wifiManager.configuredNetworks ?: return "Unknown"
            
            val currentSSID = wifiInfo.ssid?.removeSurrounding("\"")
            val config = configurations.find { 
                it.SSID?.removeSurrounding("\"") == currentSSID 
            }
            
            return when {
                config == null -> "Unknown"
                config.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK) -> "WPA/WPA2-PSK"
                config.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_EAP) -> "WPA/WPA2-Enterprise"
                config.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.NONE) -> {
                    if (config.wepKeys[0] != null) "WEP" else "Open"
                }
                else -> "Unknown"
            }
        } catch (e: Exception) {
            // Permissions may be missing or API level issues
            return "Unknown (Permission required)"
        }
    }

    private fun getWifiSSID(wifiManager: WifiManager): String? {
        return try {
            val wifiInfo = getWifiInfo(wifiManager)
            wifiInfo?.ssid?.removeSurrounding("\"")
        } catch (e: Exception) {
            null
        }
    }

    private fun getWifiInfo(wifiManager: WifiManager): WifiInfo? {
        return try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        } catch (e: Exception) {
            null
        }
    }

    private fun isProxyEnabled(): Boolean {
        return try {
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            !proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun getDnsServers(): List<String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return emptyList()
                val linkProperties = cm.getLinkProperties(network) ?: return emptyList()
                linkProperties.dnsServers.map { it.hostAddress ?: "Unknown" }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val result = mutableListOf<NetworkInterfaceInfo>()
            
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = mutableListOf<String>()
                
                val addressEnum = networkInterface.inetAddresses
                while (addressEnum.hasMoreElements()) {
                    val address = addressEnum.nextElement()
                    addresses.add("${address.hostAddress} (${if (address is java.net.Inet4Address) "IPv4" else "IPv6"})")
                }
                
                result.add(
                    NetworkInterfaceInfo(
                        name = networkInterface.name,
                        displayName = networkInterface.displayName,
                        isUp = networkInterface.isUp,
                        addresses = addresses
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Generate human-readable network security report
     */
    fun getNetworkReport(): String {
        val result = runAudit()
        return buildString {
            appendLine("=== Network Security Audit ===")
            appendLine()
            appendLine("Connection Type: ${result.connectionType}")
            appendLine("VPN Active: ${if (result.isVpnActive) "✓ Yes" else "No"}")
            
            if (result.wifiSSID != null) {
                appendLine()
                appendLine("WiFi Details:")
                appendLine("- SSID: ${result.wifiSSID}")
                appendLine("- Security: ${result.wifiSecurityType ?: "Unknown"}")
                appendLine("- Secure: ${if (result.isWifiSecure == true) "✓ Yes" else "⚠️ No"}")
            }
            
            appendLine()
            appendLine("Network Configuration:")
            appendLine("- Proxy: ${if (result.proxyEnabled) "⚠️ Enabled" else "Disabled"}")
            appendLine("- Local IP: ${result.localIpAddress ?: "N/A"}")
            
            if (result.dnsServers.isNotEmpty()) {
                appendLine("- DNS Servers:")
                result.dnsServers.forEach { dns ->
                    appendLine("  • $dns")
                }
            }
            
            if (result.networkInterfaces.isNotEmpty()) {
                appendLine()
                appendLine("Network Interfaces (${result.networkInterfaces.size}):")
                result.networkInterfaces.forEach { iface ->
                    appendLine("- ${iface.displayName} (${iface.name}): ${if (iface.isUp) "UP" else "DOWN"}")
                    iface.addresses.forEach { addr ->
                        appendLine("  • $addr")
                    }
                }
            }
            
            appendLine()
            appendLine("Security Recommendations:")
            appendSecurityRecommendations(result)
        }
    }

    private fun StringBuilder.appendSecurityRecommendations(result: NetworkSecurityResult) {
        var hasWarnings = false
        
        if (result.isWifiSecure == false) {
            appendLine("⚠️ WiFi network is not secure - avoid sensitive transactions")
            hasWarnings = true
        }
        
        if (result.wifiSecurityType?.contains("WEP") == true || result.wifiSecurityType == "Open") {
            appendLine("⚠️ Weak/No WiFi encryption - use VPN for protection")
            hasWarnings = true
        }
        
        if (!result.isVpnActive && result.connectionType == "WiFi") {
            appendLine("💡 Consider using VPN on public WiFi networks")
            hasWarnings = true
        }
        
        if (!hasWarnings) {
            appendLine("✓ No major network security issues detected")
        }
    }
}
