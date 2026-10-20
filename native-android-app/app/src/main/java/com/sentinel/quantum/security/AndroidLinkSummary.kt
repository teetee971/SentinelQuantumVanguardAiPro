package com.sentinel.quantum.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.RouteInfo
import android.os.Build

/** Passive summary only. No DNS addresses, gateway IPs, SSIDs or device identifiers leave Android. */
data class LocalLinkSummary(
    val dnsServersPresent: Boolean,
    val defaultGatewayPresent: Boolean,
    val privateDnsActive: Boolean,
    val observedAtMillis: Long,
) {
    fun isFresh(nowMillis: Long): Boolean =
        observedAtMillis >= 0L && nowMillis >= observedAtMillis &&
            nowMillis - observedAtMillis < 30_000L
}

object AndroidLinkSummary {
    fun summarize(link: LinkProperties, nowMillis: Long): LocalLinkSummary {
        val defaultRoute = link.routes.any { route: RouteInfo -> route.isDefaultRoute && route.gateway != null }
        return LocalLinkSummary(
            dnsServersPresent = link.dnsServers.isNotEmpty(),
            defaultGatewayPresent = defaultRoute,
            privateDnsActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && link.isPrivateDnsActive,
            observedAtMillis = nowMillis,
        )
    }

    fun read(context: Context, nowMillis: Long = System.currentTimeMillis()): LocalLinkSummary? {
        if (nowMillis < 0L) return null
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network = manager.activeNetwork ?: return null
            val link = manager.getLinkProperties(network) ?: return null
            summarize(link, nowMillis)
        } catch (_: SecurityException) {
            null
        }
    }
}
