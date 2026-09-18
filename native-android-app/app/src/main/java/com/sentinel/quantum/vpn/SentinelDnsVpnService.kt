package com.sentinel.quantum.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local DNS-only defensive VPN.
 *
 * Only the virtual DNS address is routed into the TUN device. Regular application traffic is not
 * captured by this service. Known ad/tracker domains receive a local NXDOMAIN response; allowed DNS
 * queries are forwarded through a socket protected from the VPN loop.
 */
class SentinelDnsVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var tunnel: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> if (running.compareAndSet(false, true)) startVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startVpn() {
        startForeground(NOTIFICATION_ID, notification())
        val descriptor = Builder()
            .setSession("Sentinel DNS Defense")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            .setBlocking(true)
            .establish()

        if (descriptor == null) {
            running.set(false)
            setRunningState(false)
            stopSelf()
            return
        }
        tunnel = descriptor
        setRunningState(true)
        executor.execute { packetLoop(descriptor) }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(8192)
        val policy = DnsFilterPolicy()
        try {
            while (running.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val query = DnsPacketCodec.parseIpv4UdpDns(buffer, length) ?: continue
                val decision = policy.evaluate(query.domain)
                val response = if (decision.blocked) {
                    DnsPacketCodec.buildNxDomain(query)
                } else {
                    val upstream = forwardDns(query.dnsPayload) ?: continue
                    DnsPacketCodec.buildIpv4UdpResponse(query, upstream)
                }
                output.write(response)
                output.flush()
            }
        } catch (_: Exception) {
            // The TUN descriptor closing during stop is an expected exit path.
        } finally {
            setRunningState(false)
            running.set(false)
            runCatching { descriptor.close() }
            tunnel = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun forwardDns(payload: ByteArray): ByteArray? {
        if (payload.isEmpty() || payload.size > 4096) return null
        val socket = DatagramSocket()
        return try {
            if (!protect(socket)) return null
            socket.soTimeout = 3000
            val upstream = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(payload, payload.size, upstream, 53))
            val response = ByteArray(4096)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            response.copyOf(packet.length)
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun stopVpn() {
        if (!running.getAndSet(false)) {
            setRunningState(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        runCatching { tunnel?.close() }
        tunnel = null
        setRunningState(false)
    }

    private fun setRunningState(active: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, active)
            .apply()
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Sentinel — filtre DNS actif")
        .setContentText("Publicités et traceurs connus filtrés localement")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Protection DNS Sentinel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "État du filtre DNS local anti-publicité et anti-traceurs"
                }
            )
        }
    }

    companion object {
        const val ACTION_STOP = "com.sentinel.quantum.vpn.STOP"
        private const val CHANNEL_ID = "sentinel_dns_vpn"
        private const val NOTIFICATION_ID = 4207
        private const val VPN_ADDRESS = "10.7.0.1"
        private const val VIRTUAL_DNS = "10.7.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val PREFS = "sentinel_dns_vpn_state"
        private const val KEY_RUNNING = "running"

        fun isRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)
    }
}
