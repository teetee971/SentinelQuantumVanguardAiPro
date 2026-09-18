package com.sentinel.quantum.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local DNS-only defensive VPN. It does not claim to provide a remote encrypted tunnel.
 * Only traffic to the synthetic DNS address is routed into the TUN interface.
 */
class SentinelDnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val stopping = AtomicBoolean(false)
    private lateinit var filter: DnsFilterPolicy

    override fun onCreate() {
        super.onCreate()
        filter = DnsFilterPolicy(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return Service.START_NOT_STICKY
        }
        startInForeground()
        if (!RUNNING.get()) startVpn()
        return Service.START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        stopping.set(false)
        val descriptor = Builder()
            .setSession("Sentinel DNS Protection")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)
            .setBlocking(true)
            .establish()
            ?: run {
                stopSelf()
                return
            }

        tun = descriptor
        RUNNING.set(true)
        worker = Thread({ packetLoop(descriptor) }, "SentinelDnsVpn").apply {
            isDaemon = true
            start()
        }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (!stopping.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val query = Ipv4UdpDnsPacket.parse(buffer, length) ?: continue
                if (query.destinationPort != DNS_PORT) continue
                val domain = query.queryName()
                val response = if (domain != null && filter.isBlocked(domain)) {
                    query.buildNxDomain()
                } else {
                    val upstream = resolveUpstream(query.dnsPayload) ?: query.buildNxDomain()
                    if (upstream.size == query.dnsPayload.size && upstream.contentEquals(query.dnsPayload)) {
                        query.buildNxDomain()
                    } else {
                        query.buildResponse(upstream)
                    }
                }
                output.write(response)
                output.flush()
            }
        } catch (_: Exception) {
            // Closing the TUN descriptor during shutdown interrupts the blocking read.
        } finally {
            RUNNING.set(false)
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    private fun resolveUpstream(query: ByteArray): ByteArray? {
        if (query.isEmpty() || query.size > MAX_DNS_PACKET) return null
        val socket = DatagramSocket()
        try {
            socket.soTimeout = 3000
            if (!protect(socket)) return null
            val target = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(query, query.size, target, DNS_PORT))
            val responseBuffer = ByteArray(MAX_DNS_PACKET)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)
            return responseBuffer.copyOf(response.length)
        } catch (_: Exception) {
            return null
        } finally {
            socket.close()
        }
    }

    private fun stopVpn() {
        if (!RUNNING.getAndSet(false) && tun == null) {
            stopSelf()
            return
        }
        stopping.set(true)
        runCatching { tun?.close() }
        tun = null
        worker?.interrupt()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SentinelDnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sentinel DNS Protection")
            .setContentText("Anti-publicité et anti-traceurs actifs via VPN local DNS")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Arrêter", stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Protection DNS locale",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "État du VPN local anti-publicité et anti-traceurs"
                }
            )
        }
    }

    companion object {
        const val ACTION_START = "com.sentinel.quantum.action.START_DNS_VPN"
        const val ACTION_STOP = "com.sentinel.quantum.action.STOP_DNS_VPN"
        val RUNNING = AtomicBoolean(false)

        private const val CHANNEL_ID = "sentinel_dns_vpn"
        private const val NOTIFICATION_ID = 2107
        private const val VPN_ADDRESS = "10.7.0.1"
        private const val VPN_DNS = "10.7.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
        private const val MAX_DNS_PACKET = 4096
    }
}
