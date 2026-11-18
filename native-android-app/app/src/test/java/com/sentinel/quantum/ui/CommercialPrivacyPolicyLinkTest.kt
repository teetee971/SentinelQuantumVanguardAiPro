package com.sentinel.quantum.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialPrivacyPolicyLinkTest {
    @Test fun v1SettingsUseTheResolverInsteadOfAHardCodedShowcaseUrl() {
        val screen = java.io.File(
            "src/main/java/com/sentinel/quantum/ui/screens/CommercialPhoneCoreScreens.kt"
        ).readText()
        assertTrue(screen.contains("CommercialPrivacyPolicyLink.url()"))
        assertFalse(screen.contains("pages.dev/public/privacy.html"))
    }

    @Test fun unresolvedV1PolicyStaysOnTheVerifiedShowcasePage() {
        assertFalse(CommercialPrivacyPolicyLink.opensPhoneCorePolicy())
        assertEquals(
            "https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html",
            CommercialPrivacyPolicyLink.url()
        )
        assertTrue(CommercialPrivacyPolicyLink.VERIFIED_V1_URL == null)
    }
}
