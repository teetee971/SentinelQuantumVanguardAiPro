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
