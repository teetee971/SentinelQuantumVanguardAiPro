package com.sentinel.quantum.security

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Local System Doctor orchestration. A scan is a collection of independently
 * attributable evidence, not an opaque health score.
 */
class SentinelSystemDoctor(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val ownCapabilities = SentinelOwnSensitiveCapabilityCollector(appContext)

    data class Scan(
        val startedAtEpochMillis: Long,
        val completedAtEpochMillis: Long,
        val evidence: List<SentinelDeviceDiagnostic.Evidence>
    ) {
        val report: SentinelDeviceDiagnostic.Report
            get() = SentinelDeviceDiagnostic.Report(evidence)
    }

    fun scan(vpnState: SentinelVpnController.RuntimeState? = null): Scan {
        val startedAt = clockMillis()
        val today = Instant.ofEpochMilli(startedAt).atZone(ZoneOffset.UTC).toLocalDate()
        val system = SentinelSystemSnapshot.capture(startedAt)
        val storage = SentinelStorageSnapshot.capture(startedAt)

        val evidence = mutableListOf<SentinelDeviceDiagnostic.Evidence>()
        evidence += SentinelSystemDiagnosticPolicy.evaluate(system, today).evidence
        evidence += SentinelStorageDiagnosticPolicy.evaluate(storage)

        val capabilityFindings = ownCapabilities.collect(startedAt)
        evidence += capabilityFindings.map(SentinelSensitiveCapabilityDiagnostic::evaluate)

        if (vpnState == null) {
            evidence += SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.vpn.runtime",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "État runtime du VPN Sentinel non fourni à ce scan.",
                observedAtEpochMillis = startedAt
            )
        } else {
            evidence += SentinelVpnDiagnostic.evidence(vpnState, startedAt)
            val vpnFinding = SentinelSensitiveCapabilityDiagnostic.Finding(
                packageName = appContext.packageName,
                capability = SentinelSensitiveCapabilityDiagnostic.Capability.VPN,
                observation = SentinelVpnDiagnostic.capabilityObservation(vpnState),
                observedAtEpochMillis = startedAt
            )
            evidence += SentinelSensitiveCapabilityDiagnostic.evaluate(vpnFinding)
        }

        val confirmedSignals = SentinelOwnCapabilityCorrelation.confirmedSignals(
            capabilityFindings + listOfNotNull(
                vpnState?.let {
                    SentinelSensitiveCapabilityDiagnostic.Finding(
                        appContext.packageName,
                        SentinelSensitiveCapabilityDiagnostic.Capability.VPN,
                        SentinelVpnDiagnostic.capabilityObservation(it),
                        startedAt
                    )
                }
            )
        )
        evidence += SentinelAppRiskCorrelation.evaluate(
            SentinelAppRiskCorrelation.Input(appContext.packageName, confirmedSignals, startedAt)
        )

        return Scan(startedAt, clockMillis(), evidence.toList())
    }
}
