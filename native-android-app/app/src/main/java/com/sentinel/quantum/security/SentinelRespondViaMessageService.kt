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
 *
 * Android can dispatch the default-handler contract with sms/smsto or mms/mmsto URIs. The
 * URI scheme is therefore part of the transport decision and is validated fail-closed: an
 * MMS quick reply is never silently downgraded to SMS and an unknown scheme is never sent.
 *
 * Unlike the interactive composer, this platform flow cannot ask the user to choose a SIM.
 * It may therefore use Android's already-selected default SMS subscription, but only when
 * that subscription is still present in the active set. Missing or stale defaults remain closed.
 */
class SentinelRespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE") {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val scheme = intent.data?.scheme?.lowercase().orEmpty()
        val destination = intent.data?.schemeSpecificPart.orEmpty().substringBefore('?')
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val appContext = applicationContext
        val submitted = runCatching {
            WORKER.execute {
                try {
                    val outcome = when (scheme) {
                        "sms", "smsto" -> {
                            val result = SentinelSmsSender(appContext).send(
                                destination = destination,
                                body = body,
                                allowAndroidDefaultWhenMultiple = true
                            )
                            QuickReplyOutcome(
                                transport = "SMS",
                                accepted = result.accepted,
                                reason = result.reason
                            )
                        }
                        "mms", "mmsto" -> {
                            val result = SentinelMmsSender(appContext).send(
                                destination = destination,
                                text = body,
                                allowAndroidDefaultWhenMultiple = true
                            )
                            QuickReplyOutcome(
                                transport = "MMS",
                                accepted = result.accepted,
                                reason = result.reason
                            )
                        }
                        else -> QuickReplyOutcome(
                            transport = "INCONNU",
                            accepted = false,
                            reason = "UNSUPPORTED_RESPOND_VIA_MESSAGE_SCHEME"
                        )
                    }
                    LocalLogger(appContext).log(
                        if (outcome.accepted) {
                            LocalLogger.LogLevel.SECURITY
                        } else {
                            LocalLogger.LogLevel.WARNING
                        },
                        "DefaultSms",
                        "Réponse ${outcome.transport} rapide: ${outcome.reason}"
                    )
                } catch (_: Exception) {
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "DefaultSms",
                        "Réponse rapide interrompue avant résultat"
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

    private data class QuickReplyOutcome(
        val transport: String,
        val accepted: Boolean,
        val reason: String
    )

    private companion object {
        val MAIN_HANDLER = Handler(Looper.getMainLooper())
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-respond-via-message").apply { isDaemon = true }
        }
    }
}
