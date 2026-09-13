package com.sentinel.quantum.vpn

import android.net.VpnService
import android.content.Intent
import android.os.ParcelFileDescriptor

class SentinelVpnService : VpnService() {
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupTunnel()
        return START_STICKY
    }

    private fun setupTunnel() {
        parcelFileDescriptor = Builder()
            .setSession("SentinelDefenseTunnel")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .establish()
    }

    override fun onDestroy() {
        super.onDestroy()
        parcelFileDescriptor?.close()
    }
}
