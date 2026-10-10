package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedLanProbeTest {
    private val allowed = NetworkAuthorizationContext(true, true, true, NetworkFeatureEntitlement.ADVANCED_LAB)
    private val denied = NetworkAuthorizationContext(true, false, true, NetworkFeatureEntitlement.ADVANCED_LAB)

    @Test fun activeProbeRequiresExplicitAuthorizedLabSession() {
        val targets = listOf(AuthorizedProbeTarget("127.0.0.1", listOf(443)))
        assertNotNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, targets))
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.DEFENSIVE_LOCAL, allowed, targets))
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, denied, targets))
    }

    @Test fun internetTargetsAndOversizedRequestsFailClosed() {
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("8.8.8.8", listOf(53)))))
        val tooManyPorts = (1..17).toList()
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("127.0.0.1", tooManyPorts))))
    }

    @Test fun unspecifiedAddressesFailClosed() {
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("0.0.0.0", listOf(443)))))
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("::", listOf(443)))))
        assertNotNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("127.0.0.1", listOf(443)))))
        assertNotNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget("::1", listOf(443)))))
    }

    @Test fun hostnameResolutionAndAmbiguousNumericTargetsFailClosed() {
        val rejected = listOf(
            "localhost", "example.com", "127.0.0.1.example.com", "127.1", "192.168.01.2",
            "999.999.999.999", "fe80::1%wlan0", "[::1]", "1::2::3",
            "::ffff:192.168.1.1", "::ffff:7f00:1",
            "127.0.0.1\\n", "127.0.0.1\\t", "1".repeat(1024)
        )
        rejected.forEach { host ->
            assertNull(host, AuthorizedLanProbePolicy.validate(
                NetworkOperationMode.AUTHORIZED_LAB, allowed, listOf(AuthorizedProbeTarget(host, listOf(443)))
            ))
        }
    }

    @Test fun executionRejectsHostnamesBeforeOpeningSockets() {
        assertNull(AuthorizedLanSocketProbe.run(NetworkOperationMode.AUTHORIZED_LAB, allowed,
            listOf(AuthorizedProbeTarget("localhost", listOf(443)))))
    }

    @Test fun connectionBudgetIsGlobalAndCountsDistinctPorts() {
        val atLimit = listOf(
            AuthorizedProbeTarget("127.0.0.1", (1..16).toList()),
            AuthorizedProbeTarget("127.0.0.2", (1..16).toList())
        )
        assertNotNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, atLimit))
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed,
            atLimit + AuthorizedProbeTarget("127.0.0.3", listOf(80))))
        val duplicates = listOf(AuthorizedProbeTarget("127.0.0.1", listOf(80, 80, 80)))
        assertTrue(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed, duplicates)!![0].ports == listOf(80))
    }

    @Test fun ipv6UniqueLocalLiteralIsAllowedWithoutNameLookup() {
        assertNotNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed,
            listOf(AuthorizedProbeTarget("fd00::1234", listOf(443)))))
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, allowed,
            listOf(AuthorizedProbeTarget("2001:4860:4860::8888", listOf(443)))))
    }

    @Test fun paidLabEntitlementRemainsMandatory() {
        val standard = NetworkAuthorizationContext(true, true, true, NetworkFeatureEntitlement.STANDARD)
        assertNull(AuthorizedLanProbePolicy.validate(NetworkOperationMode.AUTHORIZED_LAB, standard,
            listOf(AuthorizedProbeTarget("127.0.0.1", listOf(443)))))
    }

    @Test fun packetCaptureIsBoundedAndNeverAllowsDecryption() {
        assertTrue(AuthorizedPacketCapturePolicy.permits(NetworkOperationMode.AUTHORIZED_LAB, allowed, AuthorizedPacketCaptureSession(AuthorizedTrafficSource.THIS_DEVICE, 60, 1_000_000)))
        assertFalse(AuthorizedPacketCapturePolicy.permits(NetworkOperationMode.AUTHORIZED_LAB, allowed, AuthorizedPacketCaptureSession(AuthorizedTrafficSource.THIS_DEVICE, 60, 1_000_000, decryptEncryptedPayloads = true)))
        assertFalse(AuthorizedPacketCapturePolicy.permits(NetworkOperationMode.AUTHORIZED_LAB, denied, AuthorizedPacketCaptureSession(AuthorizedTrafficSource.THIS_DEVICE, 60, 1_000_000)))
    }

    @Test fun labSimulationRemainsBehindAuthorizationGate() {
        assertTrue(AuthorizedLabSimulationPolicy.permits(NetworkOperationMode.AUTHORIZED_LAB, allowed, AuthorizedLabSimulation.TCP_CONNECT_VERIFY))
        assertFalse(AuthorizedLabSimulationPolicy.permits(NetworkOperationMode.DEFENSIVE_LOCAL, allowed, AuthorizedLabSimulation.TCP_CONNECT_VERIFY))
    }
}
