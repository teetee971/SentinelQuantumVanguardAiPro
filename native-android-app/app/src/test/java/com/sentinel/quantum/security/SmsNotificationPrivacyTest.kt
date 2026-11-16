package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationPrivacyTest {
    @Test fun hiddenModeNeverCarriesSenderOrMessageText() {
        val sender = "+590690123456"
        val body = "Code secret 123456"
        val p = SmsNotificationPrivacy.presentation(false, sender, body)
        assertEquals("Nouveau message Sentinel", p.title)
        assertEquals("Ouvrez Sentinel pour lire le message.", p.text)
        assertNull(p.expandedText)
        assertFalse(p.title.contains(sender))
        assertFalse(p.text.contains(body))
    }

    @Test fun previewModeIsExplicitAndBounded() {
        val p = SmsNotificationPrivacy.presentation(true, "Alice", "x".repeat(1500))
        assertEquals("Alice", p.title)
        assertEquals(180, p.text.length)
        assertEquals(1000, p.expandedText?.length)
        assertTrue(p.expandedText!!.all { it == 'x' })
    }
}
