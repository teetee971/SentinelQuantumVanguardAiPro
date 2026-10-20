package com.sentinel.quantum.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.UUID

/**
 * Passive, local-only snapshot of Android's default network transport.
 * Never probes LAN hosts or uploads identifiers. A transport observation
 * is not evidence that a particular remote device exists or is safe.
 */
object AndroidDefaultNetworkEvidence {
    fun read(context: Context, nowMillis: Long = System.currentTimeMillis()): LocalNetworkObservation? {
        if (nowMillis < 0L || nowMillis > Long.MAX_VALUE - 30_000L) return null
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val active = manager.activeNetwork ?: return null
            val capabilities = manager.getNetworkCapabilities(active) ?: return null
            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
            // Ephemeral ID: no SSID, BSSID, MAC, network handle, IP or DNS data.
            LocalNetworkObservation(
                id = UUID.randomUUID().toString(),
                source = when (transport) {
                    "vpn" -> NetworkEvidenceSource.VPN
                    "wifi" -> NetworkEvidenceSource.ANDROID_WIFI
                    "cellular" -> NetworkEvidenceSource.ANDROID_CELLULAR
                    "ethernet" -> NetworkEvidenceSource.ANDROID_ETHERNET
                    else -> NetworkEvidenceSource.ANDROID_OTHER
                },
                observedAtMillis = nowMillis,
                expiresAtMillis = nowMillis + 30_000L,
                confidence = NetworkEvidenceConfidence.OBSERVED,
                sourceVersion = "android-default-transport-v1:$transport",
            )
        } catch (_: SecurityException) {
            null
        }
    }
}
