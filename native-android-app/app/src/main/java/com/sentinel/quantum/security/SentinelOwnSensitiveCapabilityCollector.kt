package com.sentinel.quantum.security

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Collects sensitive-capability state only for Sentinel's own package where
 * Android exposes an authoritative public API. It does not infer other apps'
 * runtime state from manifest declarations.
 */
class SentinelOwnSensitiveCapabilityCollector(context: Context) {
    private val appContext = context.applicationContext

    fun collect(nowEpochMillis: Long = System.currentTimeMillis()):
        List<SentinelSensitiveCapabilityDiagnostic.Finding> {
        val packageName = appContext.packageName
        return listOf(
            SentinelSensitiveCapabilityDiagnostic.Finding(
                packageName = packageName,
                capability = SentinelSensitiveCapabilityDiagnostic.Capability.OVERLAY,
                observation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(appContext)) {
                        SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED
                    } else {
                        SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED
                    }
                } else {
                    SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE
                },
                observedAtEpochMillis = nowEpochMillis
            ),
            SentinelSensitiveCapabilityDiagnostic.Finding(
                packageName = packageName,
                capability = SentinelSensitiveCapabilityDiagnostic.Capability.INSTALL_UNKNOWN_APPS,
                observation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { appContext.packageManager.canRequestPackageInstalls() }
                        .fold(
                            onSuccess = {
                                if (it) SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED
                                else SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED
                            },
                            onFailure = {
                                SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE
                            }
                        )
                } else {
                    SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE
                },
                observedAtEpochMillis = nowEpochMillis
            )
        )
    }
}
