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
    const val EXTRA_CALL_ID = "call.action_id"
    private const val ACTION_URI_SCHEME = "sentinel-call-action"
    private const val ACTION_URI_HOST = "call"
    private const val CHANNEL_ID = "sentinel_incoming_calls"
    private const val NOTIFICATION_ID = 5101

    fun showIncoming(
        context: Context,
        snapshot: SentinelInCallService.CallSnapshot
    ): Boolean = runCatching {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return@runCatching false
        if (!isFullScreenIntentAllowed(context)) return@runCatching false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return@runCatching false
        ensureChannel(context)
        // NotificationManager.notify() does not guarantee a visible post when the user has
        // disabled this channel. Physical certification must therefore fail closed on channel
        // importance, exactly like the SMS notification path.
        if (!isChannelEnabled(context)) return@runCatching false

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

        manager.notify(NOTIFICATION_ID, notification)
        true
    }.getOrDefault(false)

    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
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

    /**
     * Android 14+ lets the user revoke USE_FULL_SCREEN_INTENT independently of notification
     * permission. A successful notify() is not a truthful incoming-call surface when that
     * capability is denied, so callers must fail closed and use their fallback UI.
     */
    fun isFullScreenIntentAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)
                ?.canUseFullScreenIntent() == true
}
