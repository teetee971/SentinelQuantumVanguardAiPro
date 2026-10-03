package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/**
 * Handles the explicit result callback for an outgoing MMS request.
 *
 * Callback identity/result are captured synchronously; cache cleanup, replay rejection, timeline
 * writes and notifications are serialized off the BroadcastReceiver main thread.
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
        val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, Int.MIN_VALUE)
            .takeUnless { it == Int.MIN_VALUE }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scheduled = runCatching {
            WORKER.execute {
                try {
                    process(
                        appContext,
                        token,
                        fileName,
                        subscriptionId,
                        androidResultCode,
                        httpStatus
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
        token: String,
        fileName: String,
        subscriptionId: Int,
        androidResultCode: Int,
        httpStatus: Int?
    ) {
        val acceptedOnce = MmsSendCallbackReplayGuard(context).acceptOnce(token)
        if (!acceptedOnce) {
            MmsSendPduStager.delete(context, fileName)
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsSend",
                "Callback MMS dupliqué ou tombstone non persistable; aucune nouvelle transition créée"
            )
            return
        }

        MmsSendPduStager.delete(context, fileName)
        val outcome = MmsSendResultClassifier.classify(androidResultCode, httpStatus)

        runCatching {
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.MMS,
                    timestampMs = System.currentTimeMillis(),
                    direction = "OUTGOING",
                    signal = outcome.signal
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
            if (outcome.success) LocalLogger.LogLevel.SECURITY else LocalLogger.LogLevel.WARNING,
            "MmsSend",
            "Callback transport MMS; state=${outcome.diagnostic}; code=$androidResultCode; " +
                "http=${httpStatus ?: "none"}; subscription=$subscriptionId"
        )
        SmsNotificationHelper.notifyMessage(
            context,
            title = outcome.title,
            preview = outcome.preview,
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
