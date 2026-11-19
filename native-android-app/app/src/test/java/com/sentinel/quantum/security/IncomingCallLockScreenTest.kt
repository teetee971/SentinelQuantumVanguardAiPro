package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallLockScreenTest {
    @Test fun publicLockScreenContainsNeitherNumberNorName() {
        val name = "Alice Martin"
        val number = "+590690123456"
        val incoming = IncomingCallLockScreen.publicText(
            incoming = true,
            displayName = name,
            handle = number
        )
        assertEquals(IncomingCallLockScreen.MASKED_INCOMING, incoming)
        assertFalse(incoming.contains(name))
        assertFalse(incoming.contains(number))
        assertFalse(incoming.contains("690123456"))
        assertFalse(incoming.contains("Alice"))

        val ongoing = IncomingCallLockScreen.publicText(
            incoming = false,
            displayName = name,
            handle = number
        )
        assertEquals(IncomingCallLockScreen.MASKED_ONGOING, ongoing)
        assertFalse(ongoing.contains(name))
        assertFalse(ongoing.contains(number))
    }

    @Test fun helperPublishesTheMaskedTextAsThePublicVersion() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt"
        ).readText()
        assertTrue(source.contains("setPublicVersion"))
        assertTrue(source.contains("IncomingCallLockScreen.publicText"))
        assertTrue(source.contains("NotificationCompat.VISIBILITY_PRIVATE"))
        val publicNotification = source.substringAfter("fun publicLockScreenNotification")
            .substringBefore("fun cancel")
        assertFalse(publicNotification.contains("CallStyle"))
        assertFalse(publicNotification.contains("setName"))
        assertTrue(publicNotification.contains("IncomingCallLockScreen.publicText"))
    }
}
