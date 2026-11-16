package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppClickToChatTest {
    @Test fun acceptsInternationalNumber() {
        assertEquals("https://wa.me/33612345678", WhatsAppClickToChat.urlFor("+33612345678"))
    }

    @Test fun acceptsSafeHumanFormatting() {
        assertEquals("https://wa.me/33612345678", WhatsAppClickToChat.urlFor("+33 6 12 34 56 78"))
    }

    @Test fun rejectsLocalNumberWithoutCountryPrefix() {
        assertNull(WhatsAppClickToChat.urlFor("0612345678"))
    }

    @Test fun rejectsLettersAndUriInjection() {
        assertNull(WhatsAppClickToChat.urlFor("+33612345678?text=secret"))
        assertNull(WhatsAppClickToChat.urlFor("+33abc612345678"))
    }

    @Test fun rejectsNumbersOutsideE164LengthBoundary() {
        assertNull(WhatsAppClickToChat.urlFor("+1234567"))
        assertNull(WhatsAppClickToChat.urlFor("+1234567890123456"))
    }
}
