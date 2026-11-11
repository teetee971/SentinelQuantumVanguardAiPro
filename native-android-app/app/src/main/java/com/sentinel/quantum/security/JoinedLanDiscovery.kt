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

data class JoinedLanDiscoveryUpdate(
    val services: List<JoinedLanAnnouncement>,
    val running: Boolean
)

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
    private var advancedIndex = -1
    private var session = 0
    private val found = linkedMapOf<String, JoinedLanAnnouncement>()
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var resolveToken = 0
    private var activeCallback: NsdManager.ServiceInfoCallback? = null
    private var callback: ((JoinedLanDiscoveryUpdate) -> Unit)? = null

    fun start(onUpdate: (JoinedLanDiscoveryUpdate) -> Unit) {
        stop()
        if (!NetworkOperationPolicy.permitsDefensiveDiscovery(NetworkOperationMode.DEFENSIVE_LOCAL)) {
            onUpdate(JoinedLanDiscoveryUpdate(emptyList(), running = false))
            return
        }
        session += 1
        val current = session
        callback = onUpdate
        found.clear()
        pendingResolves.clear()
        resolving = false
        typeIndex = 0
        advancedIndex = -1
        val wifi = appContext.getSystemService(WifiManager::class.java)
        lock = runCatching {
            wifi?.createMulticastLock("sentinel-joined-lan")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        publish(running = true)
        discoverCurrent(current)
    }

    fun stop() {
        session += 1
        val active = listener
        listener = null
        if (active != null && nsd != null) {
            runCatching { nsd.stopServiceDiscovery(active) }
        }
        unregisterResolve()
        pendingResolves.clear()
        resolving = false
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
            publish(running = false)
            return
        }
        val type = JoinedLanServiceCatalog.types.getOrNull(typeIndex) ?: run {
            publish(running = false)
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
                if (current == session) enqueue(service, current)
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

    private fun enqueue(service: NsdServiceInfo, current: Int) {
        main.post {
            if (current != session) return@post
            pendingResolves.add(service)
            drainResolves(current)
        }
    }

    private fun drainResolves(current: Int) {
        if (current != session || resolving) return
        val next = pendingResolves.removeFirstOrNull() ?: return
        val manager = nsd ?: return
        resolving = true
        resolveToken += 1
        val token = resolveToken
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val registered = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    finishResolve(current, token)
                }
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    accept(serviceInfo)
                    unregisterResolve()
                    finishResolve(current, token)
                }
                override fun onServiceLost() {
                    unregisterResolve()
                    finishResolve(current, token)
                }
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            activeCallback = registered
            val started = runCatching { manager.registerServiceInfoCallback(next, executor, registered) }.isSuccess
            if (!started) finishResolve(current, token)
        } else {
            @Suppress("DEPRECATION")
            val started = runCatching {
                manager.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finishResolve(current, token)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        accept(serviceInfo)
                        finishResolve(current, token)
                    }
                })
            }.isSuccess
            if (!started) finishResolve(current, token)
        }
    }

    private fun finishResolve(current: Int, token: Int) {
        main.post {
            if (current != session || token != resolveToken) return@post
            resolveToken += 1
            resolving = false
            drainResolves(current)
        }
    }

    private fun unregisterResolve() {
        val manager = nsd
        val registered = activeCallback
        activeCallback = null
        if (manager != null && registered != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { manager.unregisterServiceInfoCallback(registered) }
        }
    }

    private fun accept(info: NsdServiceInfo) {
        val host = hostAddress(info) ?: return
        val announcement = JoinedLanServiceCatalog.announcement(
            name = info.serviceName.orEmpty(),
            dnsType = info.serviceType.orEmpty(),
            host = host,
            port = info.port
        ) ?: return
        main.post {
            found["${announcement.name}|${announcement.host}|${announcement.port}"] = announcement
            publish(running = true)
        }
    }

    private fun hostAddress(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fromList = info.hostAddresses.firstNotNullOfOrNull { it.hostAddress }
            if (!fromList.isNullOrBlank()) return fromList
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    private fun advance(current: Int) {
        main.post {
            if (current != session || advancedIndex == typeIndex) return@post
            advancedIndex = typeIndex
            listener = null
            typeIndex += 1
            if (typeIndex < JoinedLanServiceCatalog.types.size) {
                discoverCurrent(current)
            } else {
                publish(running = false)
            }
        }
    }

    private fun publish(running: Boolean) {
        callback?.invoke(JoinedLanDiscoveryUpdate(found.values.toList(), running))
    }

    companion object {
        private const val TYPE_WINDOW_MS = 2_500L
    }
}
