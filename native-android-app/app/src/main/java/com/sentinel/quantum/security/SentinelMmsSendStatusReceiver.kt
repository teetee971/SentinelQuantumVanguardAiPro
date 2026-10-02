package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/**
 * Handles the explicit result callback for an outgoing MMS request.
 *
 * Callback identity/result are captured synchronously; cache cleanup, timeline writes and
 * notifications are serialized off the BroadcastReceiver main thread.
 */
class SentinelMmsSendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SentinelMmsSender.ACTION_SENT) return
        val callbackUri = intent.data ?: return
        if (callbackUri.scheme != "sentinel-mms-send" || callbackUri.host != "result") return

        val token = callbackUri.pathSegments.singleOrNull()
            ?.takeIf { TOKEN.matches(it) }
            ?: return
        val fileName = intent.getStringExtra(SentinelMmsSender.EXTRA_FILE_NAME)
            ?.takeIf { it == "$token.pdu" && FILE_NAME.matches(it) }
            ?: return
        val subscriptionId = intent.getIntExtra(
            SentinelMmsSender.EXTRA_SUBSCRIPTION_ID,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return

        val androidResultCode = resultCode
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scheduled = runCatching {
            WORKER.execute {
                try {
                    process(
                        appContext,
                        fileName,
                        subscriptionId,
                        androidResultCode
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }.isSuccess
        if (!scheduled) pendingResult.finish()
    }

    private fun process(
        context: Context,
        fileName: String,
        subscriptionId: Int,
        androidResultCode: Int
    ) {
        MmsSendPduStager.delete(context, fileName)
        val success = androidResultCode == Activity.RESULT_OK
        val signal = if (success) PhoneCorePhysicalValidation.SIGNAL_MMS_SENT_OK else "MMS_SEND_ERROR_$androidResultCode"

        runCatching {
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.MMS,
                    timestampMs = System.currentTimeMillis(),
                    direction = "OUTGOING",
                    signal = signal
                )
            )
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsSend",
                "Chronologie privée indisponible; le callback MMS reste traité"
            )
        }

        LocalLogger(context).log(
            if (success) LocalLogger.LogLevel.SECURITY else LocalLogger.LogLevel.WARNING,
            "MmsSend",
            if (success) {
                "MMS accepté par la pile opérateur Android; subscription=$subscriptionId"
            } else {
                "Échec MMS signalé par Android; code=$androidResultCode; subscription=$subscriptionId"
            }
        )
        SmsNotificationHelper.notifyMessage(
            context,
            title = if (success) "MMS envoyé" else "Échec d’envoi MMS",
            preview = if (success) {
                "Android a confirmé l’envoi du MMS."
            } else {
                "La pile téléphonie a refusé ou échoué à envoyer le MMS."
            },
            notificationId = fileName.hashCode()
        )
    }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-mms-send").apply { isDaemon = true }
        }
        val TOKEN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        val FILE_NAME = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdu$"
        )
    }
}
