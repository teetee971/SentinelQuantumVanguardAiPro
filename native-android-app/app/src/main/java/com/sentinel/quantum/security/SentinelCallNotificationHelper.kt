package com.sentinel.quantum.security

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelInCallActivity

/**
 * Incoming-call notification surface for the user-selected default dialer.
 *
 * Telecom keeps responsibility for the ringtone because Sentinel does not declare
 * IN_CALL_SERVICE_RINGING. This helper only provides the heads-up/full-screen UI path.
 */
object SentinelCallNotificationHelper {
    const val ACTION_ANSWER = "com.sentinel.quantum.CALL_ANSWER"
    const val ACTION_REJECT = "com.sentinel.quantum.CALL_REJECT"
    const val ACTION_DISCONNECT = "com.sentinel.quantum.CALL_DISCONNECT"
    const val EXTRA_CALL_ID = "call.action_id"
    private const val ACTION_URI_SCHEME = "sentinel-call-action"
    private const val ACTION_URI_HOST = "call"
    private const val CHANNEL_ID = "sentinel_incoming_calls"
    private const val NOTIFICATION_ID = 5101

    fun showIncoming(
        context: Context,
        snapshot: SentinelInCallService.CallSnapshot
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        // NotificationManager.notify() does not guarantee a visible post when the user has
        // disabled this channel. Physical certification must therefore fail closed on channel
        // importance, exactly like the SMS notification path.
        if (!isChannelEnabled(context)) return false

        val label = snapshot.displayName?.takeIf { it.isNotBlank() }
            ?: snapshot.handle?.takeIf { it.isNotBlank() }
            ?: "Appel entrant"
        val quickTrust = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = !snapshot.displayName.isNullOrBlank(),
                localDecision = null,
                premiumRateCaution = snapshot.handle
                    ?.let(PhoneNumberRiskRules::isKnownPremiumRatePrefix)
                    ?: false
            )
        )

        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SentinelInCallActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun callAction(action: String, kind: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, SentinelCallActionReceiver::class.java)
                    .setAction(action)
                    .setData(
                        Uri.parse(
                            "$ACTION_URI_SCHEME://$ACTION_URI_HOST/" +
                                Uri.encode(snapshot.id) +
                                "/$kind"
                        )
                    )
                    .putExtra(EXTRA_CALL_ID, snapshot.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val answer = callAction(ACTION_ANSWER, "answer", 1)
        val reject = callAction(ACTION_REJECT, "reject", 2)
        val caller = Person.Builder()
            .setName("$label · ${quickTrust.title}")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, reject, answer))
            .build()

        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    /**
     * Persistent user-accessible route back to the real Telecom call surface.
     * Never starts an activity directly from a background service; the user taps contentIntent.
     * The disconnect action is bound to the exact call ID and validated by the receiver.
     */
    fun showOngoing(
        context: Context,
        snapshot: SentinelInCallService.CallSnapshot
    ): Boolean {
        if (snapshot.state == android.telecom.Call.STATE_RINGING ||
            snapshot.state == android.telecom.Call.STATE_DISCONNECTED ||
            snapshot.state == android.telecom.Call.STATE_DISCONNECTING) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        if (!isChannelEnabled(context)) return false

        val openCall = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SentinelInCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, SentinelCallActionReceiver::class.java)
                .setAction(ACTION_DISCONNECT)
                .setData(Uri.parse("$ACTION_URI_SCHEME://$ACTION_URI_HOST/" +
                    Uri.encode(snapshot.id) + "/disconnect"))
                .putExtra(EXTRA_CALL_ID, snapshot.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = snapshot.displayName?.takeIf { it.isNotBlank() }
            ?: snapshot.handle?.takeIf { it.isNotBlank() }
            ?: "Appel en cours"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(
                if (snapshot.state == android.telecom.Call.STATE_ACTIVE) "En communication · toucher pour ouvrir"
                else "Appel en cours · toucher pour ouvrir"
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall)
            .addAction(R.mipmap.ic_launcher, "Raccrocher", disconnect)
        if (snapshot.state == android.telecom.Call.STATE_ACTIVE) {
            snapshot.connectedAtMs?.takeIf { it > 0L }?.let {
                builder.setWhen(it).setShowWhen(true).setUsesChronometer(true)
            }
        }
        return runCatching {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Appels entrants Sentinel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interface d’appel entrant du composeur Sentinel"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun isChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        ensureChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
