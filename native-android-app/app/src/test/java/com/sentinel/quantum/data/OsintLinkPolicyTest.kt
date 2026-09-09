package com.sentinel.quantum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintLinkPolicyTest {
    @Test fun acceptsAbsoluteHttpsUrl() =
        assertTrue(OsintLinkPolicy.isSafeHttpsUrl("https://www.cert.ssi.gouv.fr/avis/"))

    @Test fun rejectsNonHttpsAndMissingHost() {
        assertFalse(OsintLinkPolicy.isSafeHttpsUrl("http://example.org"))
        assertFalse(OsintLinkPolicy.isSafeHttpsUrl("https:///missing-host"))
        assertFalse(OsintLinkPolicy.isSafeHttpsUrl("javascript:alert(1)"))
    }
}
