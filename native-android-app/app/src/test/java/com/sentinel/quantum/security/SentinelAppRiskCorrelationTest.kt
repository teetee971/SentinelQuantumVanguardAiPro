package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SentinelAppRiskCorrelationTest {
    private fun input(vararg signals: SentinelAppRiskCorrelation.Signal) =
        SentinelAppRiskCorrelation.Input("example.app", signals.toSet(), 1L)

    @Test
    fun noConfirmedSignalsIsOkForCorrelationOnly() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.OK,
            SentinelAppRiskCorrelation.evaluate(input()).status
        )
    }

    @Test
    fun vpnAloneIsInformationalNotRiskWarning() {
        val evidence = SentinelAppRiskCorrelation.evaluate(
            input(SentinelAppRiskCorrelation.Signal.VPN_ENABLED)
        )
        assertEquals(SentinelDeviceDiagnostic.Status.OK, evidence.status)
        assertFalse(evidence.summary.contains("risque", ignoreCase = true))
    }

    @Test
    fun singleNonVpnSensitiveCapabilityStaysWarning() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelAppRiskCorrelation.evaluate(
                input(SentinelAppRiskCorrelation.Signal.OVERLAY_ENABLED)
            ).status
        )
    }

    @Test
    fun accessibilityPlusOverlayIsElevatedButNotCritical() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelAppRiskCorrelation.evaluate(
                input(
                    SentinelAppRiskCorrelation.Signal.ACCESSIBILITY_ENABLED,
                    SentinelAppRiskCorrelation.Signal.OVERLAY_ENABLED
                )
            ).status
        )
    }

    @Test
    fun accessibilityOverlayAndUnknownInstallIsCriticalCombination() {
        val evidence = SentinelAppRiskCorrelation.evaluate(
            input(
                SentinelAppRiskCorrelation.Signal.ACCESSIBILITY_ENABLED,
                SentinelAppRiskCorrelation.Signal.OVERLAY_ENABLED,
                SentinelAppRiskCorrelation.Signal.UNKNOWN_APP_INSTALL_ENABLED
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.CRITICAL, evidence.status)
        assertFalse(evidence.summary.contains("preuve de malware", ignoreCase = true).not())
    }

    @Test
    fun deviceAdminPlusUnknownInstallIsElevated() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelAppRiskCorrelation.evaluate(
                input(
                    SentinelAppRiskCorrelation.Signal.DEVICE_ADMIN_ENABLED,
                    SentinelAppRiskCorrelation.Signal.UNKNOWN_APP_INSTALL_ENABLED
                )
            ).status
        )
    }
}
