package com.sentinel.quantum.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SentinelRespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        if (destination.isNotBlank() && text.isNotBlank()) {
            SmsProviderStore.sendText(this, destination, text)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
