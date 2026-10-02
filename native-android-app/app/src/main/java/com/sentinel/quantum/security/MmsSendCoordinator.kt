package com.sentinel.quantum.security

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/** Public-API-only outgoing MMS submission boundary. A request is not a delivery receipt. */
object MmsSendCoordinator {
    sealed class Result {
        data class Requested(val token: String, val subscriptionId: Int) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun request(context: Context, subscriptionId: Int, pdu: ByteArray): Result {
        if (context.readSmsRoleStateFailClosed() != SmsActivationDiagnostics.SmsRoleState.HELD) {
            return Result.Rejected("SMS_ROLE_NOT_HELD")
        }
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || subscriptionId < 0) {
            return Result.Rejected("MMS_SUBSCRIPTION_REQUIRED")
        }

        val staged = MmsSendPduStager.stage(context, pdu)
        if (staged !is MmsSendPduStager.Result.Staged) {
            return Result.Rejected((staged as MmsSendPduStager.Result.Rejected).reason)
        }

        val callbackIntent = Intent(context, SentinelMmsSendReceiver::class.java)
            .setAction(ACTION_SEND_COMPLETE)
            .setData(Uri.parse("sentinel-mms-send://callback/" + staged.token))
            .putExtra(EXTRA_TOKEN, staged.token)
            .putExtra(EXTRA_FILE_NAME, staged.fileName)
            .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
        val callback = PendingIntent.getBroadcast(
            context,
            staged.token.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
            manager.sendMultimediaMessage(context, staged.contentUri, null, null, callback)
            Result.Requested(staged.token, subscriptionId)
        } catch (_: Exception) {
            MmsSendPduStager.delete(context, staged.fileName)
            Result.Rejected("MMS_SEND_REQUEST_FAILED")
        }
    }

    const val ACTION_SEND_COMPLETE = "com.sentinel.quantum.MMS_SEND_COMPLETE"
    const val EXTRA_TOKEN = "mms.send.token"
    const val EXTRA_FILE_NAME = "mms.send.file"
    const val EXTRA_SUBSCRIPTION_ID = "mms.send.subscription"
}
