package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsConversationIdentityPolicyTest {
    @Test
    fun knownAndroidContactUsesLocalNameAndKeepsCanonicalAddress() {
        val resolved = SmsConversationIdentityPolicy.resolve(
            address = "+590690123456",
            localIdentity = LocalContactLookup.Identity("Alice", null)
        )

        assertEquals("+590690123456", resolved.address)
        assertEquals("Alice", resolved.displayLabel)
        assertEquals(
            SmsConversationIdentityPolicy.Provenance.ANDROID_CONTACT,
            resolved.provenance
        )
    }

    @Test
    fun unknownContactFallsBackToRawAddress() {
        val resolved = SmsConversationIdentityPolicy.resolve(
            address = "+590690123456",
            localIdentity = null
        )

        assertEquals("+590690123456", resolved.address)
        assertEquals("+590690123456", resolved.displayLabel)
        assertEquals(SmsConversationIdentityPolicy.Provenance.UNKNOWN, resolved.provenance)
    }

    @Test
    fun blankContactNameDoesNotClaimAndroidContactProvenance() {
        val resolved = SmsConversationIdentityPolicy.resolve(
            address = "+590690123456",
            localIdentity = LocalContactLookup.Identity("   ", "Example")
        )

        assertEquals("+590690123456", resolved.displayLabel)
        assertEquals(SmsConversationIdentityPolicy.Provenance.UNKNOWN, resolved.provenance)
    }
}
