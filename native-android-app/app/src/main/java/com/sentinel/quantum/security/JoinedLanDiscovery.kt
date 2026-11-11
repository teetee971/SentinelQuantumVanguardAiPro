package com.sentinel.quantum.security

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.security.MessageDigest
import java.util.concurrent.Executors

data class JoinedLanAnnouncement(
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val service: ObservedNetworkService
)

/** Service types a phone may learn by mDNS on the network it has joined. No address sweep. */
object JoinedLanServiceCatalog {
    data class Type(
        val dnsType: String,
        val hint: String,
        val defaultPort: Int
    )

    val types: List<Type> = listOf(
        Type("_http._tcp.", "http", 80),
        Type("_https._tcp.", "https", 443),
        Type("_googlecast._tcp.", "googlecast", 8009),
        Type("_airplay._tcp.", "airplay", 7000),
        Type("_ipp._tcp.", "ipp", 631),
        Type("_smb._tcp.", "smb", 445),
        Type("_ssh._tcp.", "ssh", 22),
        Type("_hap._tcp.", "homekit", 8080)
    )

    fun fingerprint(name: String, host: String, port: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$name|$host|$port".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun announcement(name: String, dnsType: String, host: String, port: Int): JoinedLanAnnouncement? {
        val type = types.firstOrNull { sameType(it.dnsType, dnsType) } ?: return null
        val usablePort = port.takeIf { it in 1..65535 } ?: type.defaultPort
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return null
        val fingerprint = fingerprint(name, cleanHost, usablePort)
        return JoinedLanAnnouncement(
            name = name.ifBlank { dnsType },
            type = dnsType,
            host = cleanHost,
            port = usablePort,
            service = ObservedNetworkService(
                subjectFingerprint = fingerprint,
                port = usablePort,
                transport = NetworkServiceTransport.TCP,
                scope = NetworkServiceScope.LOCAL_LAN,
                serviceHint = type.hint
            )
        )
    }

    private fun sameType(expected: String, actual: String): Boolean {
        fun normalize(value: String) = value.lowercase().removeSuffix("local.").trim('.')
        val left = normalize(expected)
        val right = normalize(actual)
        return left == right || right.endsWith(left)
    }
}

/**
 * mDNS on the joined Wi-Fi only. Defensive discovery is allowed.
 * The TCP lab probe stays unused: it needs an explicit authorized session.
 */
class JoinedLanDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var typeIndex = 0
    private var session = 0
    private val found = linkedMapOf<String, JoinedLanAnnouncement>()
    private var callback: ((List<JoinedLanAnnouncement>) -> Unit)? = null

    fun start(onUpdate: (List<JoinedLanAnnouncement>) -> Unit) {
        stop()
        if (!NetworkOperationPolicy.permitsDefensiveDiscovery(NetworkOperationMode.DEFENSIVE_LOCAL)) {
            onUpdate(emptyList())
            return
        }
        session += 1
        val current = session
        callback = onUpdate
        found.clear()
        typeIndex = 0
        val wifi = appContext.getSystemService(WifiManager::class.java)
        lock = runCatching {
            wifi?.createMulticastLock("sentinel-joined-lan")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        discoverCurrent(current)
    }

    fun stop() {
        session += 1
        val active = listener
        listener = null
        if (active != null && nsd != null) {
            runCatching { nsd.stopServiceDiscovery(active) }
        }
        lock?.let { if (it.isHeld) runCatching { it.release() } }
        lock = null
        callback = null
    }

    fun close() {
        stop()
        executor.shutdownNow()
    }

    private fun discoverCurrent(current: Int) {
        if (current != session) return
        val manager = nsd ?: run {
            publish()
            return
        }
        val type = JoinedLanServiceCatalog.types.getOrNull(typeIndex) ?: run {
            publish()
            return
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) {
                advance(current)
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                advance(current)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                advance(current)
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (current == session) resolve(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
        }
        listener = discovery
        val started = runCatching {
            manager.discoverServices(type.dnsType, NsdManager.PROTOCOL_DNS_SD, discovery)
        }.isSuccess
        if (!started) {
            advance(current)
            return
        }
        main.postDelayed({
            if (current == session && listener === discovery) {
                runCatching { manager.stopServiceDiscovery(discovery) }
            }
        }, TYPE_WINDOW_MS)
    }

    private fun resolve(service: NsdServiceInfo) {
        val manager = nsd ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.resolveService(
                service,
                executor,
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        accept(serviceInfo)
                    }
                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
            )
        } else {
            @Suppress("DEPRECATION")
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    accept(serviceInfo)
                }
            })
        }
    }

    private fun accept(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        val announcement = JoinedLanServiceCatalog.announcement(
            name = info.serviceName.orEmpty(),
            dnsType = info.serviceType.orEmpty(),
            host = host,
            port = info.port
        ) ?: return
        main.post {
            found["${announcement.name}|${announcement.host}|${announcement.port}"] = announcement
            publish()
        }
    }

    private fun advance(current: Int) {
        main.post {
            if (current != session) return@post
            listener = null
            typeIndex += 1
            if (typeIndex < JoinedLanServiceCatalog.types.size) discoverCurrent(current) else publish()
        }
    }

    private fun publish() {
        callback?.invoke(found.values.toList())
    }

    companion object {
        private const val TYPE_WINDOW_MS = 1_500L
    }
}
