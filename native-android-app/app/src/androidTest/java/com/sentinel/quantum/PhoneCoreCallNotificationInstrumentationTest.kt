package com.sentinel.quantum

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SentinelInCallService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneCoreCallNotificationInstrumentationTest {
    @Test fun answeringReplacesIncomingSurfaceWithOngoingCallControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        assumeTrue(SentinelCallNotificationHelper.isChannelEnabled(context))
        val snapshot = SentinelInCallService.CallSnapshot(
            id = "call-00000000000000000000000000000000-1", state = Call.STATE_RINGING,
            direction = "INCOMING", connectedAtMs = null, displayName = "Test Phone Core",
            handle = null, canHold = false, canMute = false, canMergeConference = false,
            canSwapConference = false, isMuted = null, audioRoutes = emptyList(), audioStatus = null
        )
        try {
            assertTrue(SentinelCallNotificationHelper.showIncoming(context, snapshot))
            assertTrue(SentinelCallNotificationHelper.showOngoing(context,
                snapshot.copy(state = Call.STATE_ACTIVE)))
            val posted = context.getSystemService(NotificationManager::class.java)
                .activeNotifications.single { it.id == 5101 }.notification
            assertEquals(Notification.CATEGORY_CALL, posted.category)
            assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(posted.contentIntent)
            assertNull("Only incoming calls may request full screen", posted.fullScreenIntent)
        } finally {
            SentinelCallNotificationHelper.cancel(context)
        }
    }
}
