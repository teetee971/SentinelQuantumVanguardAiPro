package com.sentinel.quantum.security

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Handles ACTION_RESPOND_VIA_MESSAGE when Sentinel is the user-selected default SMS app.
 */
class SentinelRespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE") {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val destination = intent.data?.schemeSpecificPart.orEmpty()
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val result = SentinelSmsSender(applicationContext).send(destination, body)
        LocalLogger(applicationContext).log(
            if (result.accepted) LocalLogger.LogLevel.SECURITY else LocalLogger.LogLevel.WARNING,
            "DefaultSms",
            "Réponse SMS rapide: " + result.reason
        )
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
