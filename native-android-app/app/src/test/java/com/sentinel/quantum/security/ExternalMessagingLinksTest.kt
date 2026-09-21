package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMessagingLinksTest {
    @Test fun buildsTelegramPublicHandle() {
        assertEquals("https://t.me/sentinel_user", ExternalMessagingLinks.url(ExternalMessagingLinks.Service.TELEGRAM, "@sentinel_user"))
    }

    @Test fun buildsInstagramAndMessengerPublicHandles() {
        assertEquals("https://www.instagram.com/sentinel.user/", ExternalMessagingLinks.url(ExternalMessagingLinks.Service.INSTAGRAM, "sentinel.user"))
        assertEquals("https://m.me/sentinel_user", ExternalMessagingLinks.url(ExternalMessagingLinks.Service.MESSENGER, "sentinel_user"))
    }

    @Test fun rejectsUriInjectionAndPathTraversal() {
        assertNull(ExternalMessagingLinks.url(ExternalMessagingLinks.Service.TELEGRAM, "user?text=secret"))
        assertNull(ExternalMessagingLinks.url(ExternalMessagingLinks.Service.INSTAGRAM, "../user"))
        assertNull(ExternalMessagingLinks.url(ExternalMessagingLinks.Service.MESSENGER, "user/name"))
    }

    @Test fun doesNotInventSignalOrDiscordDestinations() {
        assertNull(ExternalMessagingLinks.url(ExternalMessagingLinks.Service.SIGNAL, "sentinel"))
        assertNull(ExternalMessagingLinks.url(ExternalMessagingLinks.Service.DISCORD, "sentinel"))
    }
}
