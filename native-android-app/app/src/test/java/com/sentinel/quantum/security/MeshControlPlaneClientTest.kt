package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshControlPlaneClientTest {

    @Test
    fun endpointValidationAcceptsBoundedIpv4HostnameAndIpv6Forms() {
        assertTrue(MeshControlPlaneClient.validEndpoint("198.51.100.10:51820"))
        assertTrue(MeshControlPlaneClient.validEndpoint("relay.example.test:3480"))
        assertTrue(MeshControlPlaneClient.validEndpoint("[2001:db8::1]:51820"))
        assertFalse(MeshControlPlaneClient.validEndpoint("198.51.100.10:0"))
        assertFalse(MeshControlPlaneClient.validEndpoint("198.51.100.10:70000"))
        assertFalse(MeshControlPlaneClient.validEndpoint("not-an-endpoint"))
    }

    @Test
    fun sessionIdsRejectControlCharactersAndOversizeValues() {
        assertTrue(MeshControlPlaneClient.validSessionId("device:a->device:b:1000:1"))
        assertFalse(MeshControlPlaneClient.validSessionId(""))
        assertFalse(MeshControlPlaneClient.validSessionId("bad\nvalue"))
        assertFalse(MeshControlPlaneClient.validSessionId("x".repeat(513)))
    }

    @Test
    fun jsonQuoteEscapesControlAndStructuralCharacters() {
        assertEquals(
            "\"a\\\"b\\\\c\\n\"",
            MeshControlPlaneClient.quote("a\"b\\c\n")
        )
    }

    @Test
    fun invitationCodesAreBoundedBase64UrlTokens() {
        assertTrue(MeshControlPlaneClient.validInvitationCode("A".repeat(43)))
        assertFalse(MeshControlPlaneClient.validInvitationCode("short"))
        assertFalse(MeshControlPlaneClient.validInvitationCode("A".repeat(42) + "!"))
    }

    @Test
    fun enrollmentCredentialParserAcceptsOnlyBoundedNodeAndTokenFields() {
        val parsed = MeshControlPlaneClient.parseEnrollmentCredential(
            """{"nodeId":"device:android-01","token":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}"""
        )
        assertEquals("device:android-01", parsed?.nodeId)
        assertEquals("A".repeat(43), parsed?.token)

        assertEquals(
            null,
            MeshControlPlaneClient.parseEnrollmentCredential(
                """{"nodeId":"bad node","token":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}"""
            )
        )
    }

    @Test
    fun nodeIdsRemainStrictlyBounded() {
        assertTrue(MeshNodeCredentialStore.validNodeId("device:android-01"))
        assertFalse(MeshNodeCredentialStore.validNodeId("a"))
        assertFalse(MeshNodeCredentialStore.validNodeId("device with spaces"))
        assertFalse(MeshNodeCredentialStore.validNodeId("x".repeat(257)))
    }
}
