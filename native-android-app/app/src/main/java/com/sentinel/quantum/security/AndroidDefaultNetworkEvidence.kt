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
    /** VPN takes precedence: underlying transport is not independently confirmed here. */
    fun classifyTransport(
        vpn: Boolean,
        wifi: Boolean,
        cellular: Boolean,
        ethernet: Boolean,
    ): LocalObservationSource = when {
        vpn -> LocalObservationSource.VPN
        wifi -> LocalObservationSource.ANDROID_WIFI
        cellular -> LocalObservationSource.ANDROID_CELLULAR
        ethernet -> LocalObservationSource.ANDROID_ETHERNET
        else -> LocalObservationSource.ANDROID_OTHER
    }

    fun read(context: Context, nowMillis: Long = System.currentTimeMillis()): LocalNetworkObservation? {
        if (nowMillis < 0L || nowMillis > Long.MAX_VALUE - 30_000L) return null
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val active = manager.activeNetwork ?: return null
            val capabilities = manager.getNetworkCapabilities(active) ?: return null
            val source = classifyTransport(
                vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            )
            // Ephemeral ID: no SSID, BSSID, MAC, network handle, IP or DNS data.
            LocalNetworkObservation(
                id = UUID.randomUUID().toString(),
                source = source,
                observedAtMillis = nowMillis,
                expiresAtMillis = nowMillis + 30_000L,
                confidence = NetworkEvidenceConfidence.OBSERVED,
                sourceVersion = "android-default-transport-v1",
            )
        } catch (_: SecurityException) {
            null
        }
    }
}
