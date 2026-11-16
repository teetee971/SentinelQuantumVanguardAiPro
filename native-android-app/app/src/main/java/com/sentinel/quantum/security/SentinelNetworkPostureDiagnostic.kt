package com.sentinel.quantum.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Permission-minimal snapshot of the app's currently observable default network.
 * This does not claim that the network is trusted, encrypted end-to-end, or free
 * of interception.
 */
object SentinelNetworkPostureDiagnostic {
    data class Snapshot(
        val collectionSucceeded: Boolean,
        val hasActiveNetwork: Boolean,
        val internetCapability: Boolean?,
        val validatedCapability: Boolean?,
        val vpnTransport: Boolean?,
        val observedAtEpochMillis: Long
    )

    fun capture(context: Context, observedAtEpochMillis: Long = System.currentTimeMillis()): Snapshot {
        return runCatching {
            val manager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork
            if (network == null) {
                Snapshot(true, false, null, null, null, observedAtEpochMillis)
            } else {
                val caps = manager.getNetworkCapabilities(network)
                Snapshot(
                    collectionSucceeded = caps != null,
                    hasActiveNetwork = true,
                    internetCapability = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validatedCapability = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    vpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    observedAtEpochMillis = observedAtEpochMillis
                )
            }
        }.getOrElse {
            Snapshot(false, false, null, null, null, observedAtEpochMillis)
        }
    }

    fun evaluate(snapshot: Snapshot): List<SentinelDeviceDiagnostic.Evidence> {
        if (!snapshot.collectionSucceeded) {
            return listOf(
                SentinelDeviceDiagnostic.Evidence(
                    id = "network.default",
                    status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                    summary = "Posture du réseau actif non observable.",
                    observedAtEpochMillis = snapshot.observedAtEpochMillis
                )
            )
        }

        if (!snapshot.hasActiveNetwork) {
            return listOf(
                SentinelDeviceDiagnostic.Evidence(
                    id = "network.default",
                    status = SentinelDeviceDiagnostic.Status.WARNING,
                    summary = "Aucun réseau actif observé pour Sentinel.",
                    observedAtEpochMillis = snapshot.observedAtEpochMillis
                )
            )
        }

        return listOf(
            SentinelDeviceDiagnostic.Evidence(
                id = "network.internet",
                status = if (snapshot.internetCapability == true) SentinelDeviceDiagnostic.Status.OK else SentinelDeviceDiagnostic.Status.WARNING,
                summary = if (snapshot.internetCapability == true) "Le réseau actif annonce une capacité Internet." else "Le réseau actif n'annonce pas de capacité Internet.",
                observedValue = snapshot.internetCapability?.toString(),
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            ),
            SentinelDeviceDiagnostic.Evidence(
                id = "network.validated",
                status = if (snapshot.validatedCapability == true) SentinelDeviceDiagnostic.Status.OK else SentinelDeviceDiagnostic.Status.WARNING,
                summary = if (snapshot.validatedCapability == true) "Android a validé la connectivité du réseau actif." else "Android n'a pas validé la connectivité du réseau actif.",
                observedValue = snapshot.validatedCapability?.toString(),
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            ),
            SentinelDeviceDiagnostic.Evidence(
                id = "network.vpn_transport",
                status = SentinelDeviceDiagnostic.Status.OK,
                summary = if (snapshot.vpnTransport == true) "Le réseau actif observé utilise un transport VPN." else "Aucun transport VPN n'est observé sur le réseau actif par ce contrôle.",
                observedValue = snapshot.vpnTransport?.toString(),
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            )
        )
    }
}
