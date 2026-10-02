package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import java.util.concurrent.Executors

/** Validates the identity-bound MMS telephony callback and records submission outcome only. */
class SentinelMmsSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsSendCoordinator.ACTION_SEND_COMPLETE) return
        val uri = intent.data ?: return
        if (uri.scheme != "sentinel-mms-send" || uri.host != "callback") return
        val segments = uri.pathSegments
        val uriToken = segments.singleOrNull()
        val token = intent.getStringExtra(MmsSendCoordinator.EXTRA_TOKEN)
        val fileName = intent.getStringExtra(MmsSendCoordinator.EXTRA_FILE_NAME)
        val subscriptionId = intent.getIntExtra(MmsSendCoordinator.EXTRA_SUBSCRIPTION_ID, -1)
        if (!MmsSendCallbackPolicy.accepts(
                MmsSendCallbackPolicy.Input(
                    action = intent.action,
                    scheme = uri.scheme,
                    host = uri.host,
                    pathSegments = segments,
                    uriToken = uriToken,
                    extraToken = token,
                    fileName = fileName,
                    subscriptionId = subscriptionId
                )
            )
        ) return
        val validatedToken = token ?: return
        val validatedFileName = fileName ?: return

        val androidResultCode = resultCode
        val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1)
        val appContext = context.applicationContext
        val pending = goAsync()
        try {
            EXECUTOR.execute {
                try {
                    MmsSendPduStager.delete(appContext, validatedFileName)
                    val signal = if (androidResultCode == Activity.RESULT_OK) {
                        "MMS_SENT_OK"
                    } else {
                        "MMS_SEND_ERROR_" + androidResultCode
                    }
                    LocalLogger(appContext).log(
                        if (androidResultCode == Activity.RESULT_OK) LocalLogger.LogLevel.SECURITY else LocalLogger.LogLevel.WARNING,
                        "MmsSend",
                        if (httpStatus >= 0) signal + " HTTP_" + httpStatus else signal
                    )
                } finally {
                    pending.finish()
                }
            }
        } catch (_: RuntimeException) {
            pending.finish()
        }
    }

    private companion object {
        val EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "SentinelMmsSendStatus").apply { isDaemon = true }
        }
    }
}
