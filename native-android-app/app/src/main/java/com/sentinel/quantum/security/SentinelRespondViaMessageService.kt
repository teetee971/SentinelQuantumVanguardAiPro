package com.sentinel.quantum.security

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Handles ACTION_RESPOND_VIA_MESSAGE when Sentinel is the user-selected default SMS app.
 *
 * Service.onStartCommand() runs on the application's main thread. Quick-reply validation,
 * provider access and SmsManager work are therefore serialized on a private worker, while
 * lifecycle completion is posted back to the main looper with the exact startId.
 */
class SentinelRespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE") {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val destination = intent.data?.schemeSpecificPart.orEmpty()
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val appContext = applicationContext
        val submitted = runCatching {
            WORKER.execute {
                try {
                    val result = SentinelSmsSender(appContext).send(destination, body)
                    LocalLogger(appContext).log(
                        if (result.accepted) {
                            LocalLogger.LogLevel.SECURITY
                        } else {
                            LocalLogger.LogLevel.WARNING
                        },
                        "DefaultSms",
                        "Réponse SMS rapide: " + result.reason
                    )
                } catch (_: Exception) {
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "DefaultSms",
                        "Réponse SMS rapide interrompue avant résultat"
                    )
                } finally {
                    MAIN_HANDLER.post {
                        // Do not stop the service if a newer start request is still pending.
                        stopSelfResult(startId)
                    }
                }
            }
        }.isSuccess

        if (!submitted) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private companion object {
        val MAIN_HANDLER = Handler(Looper.getMainLooper())
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-respond-via-message").apply { isDaemon = true }
        }
    }
}
