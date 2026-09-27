package com.sentinel.quantum.security

import android.content.Context
import android.provider.Settings

/**
 * Local posture observation for Android developer settings.
 *
 * These settings are security-relevant context, not malware indicators. A read
 * failure remains UNKNOWN and is never converted to a healthy state.
 */
object SentinelDeveloperPostureDiagnostic {
    data class Snapshot(
        val developerOptionsEnabled: Boolean?,
        val adbEnabled: Boolean?,
        val observedAtEpochMillis: Long
    )

    fun capture(context: Context, observedAtEpochMillis: Long = System.currentTimeMillis()): Snapshot {
        val resolver = context.applicationContext.contentResolver
        fun read(name: String): Boolean? = runCatching {
            Settings.Global.getInt(resolver, name, 0) != 0
        }.getOrNull()

        return Snapshot(
            developerOptionsEnabled = read(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            adbEnabled = read(Settings.Global.ADB_ENABLED),
            observedAtEpochMillis = observedAtEpochMillis
        )
    }

    fun evaluate(snapshot: Snapshot): List<SentinelDeviceDiagnostic.Evidence> = listOf(
        evidence(
            id = "android.developer_options",
            value = snapshot.developerOptionsEnabled,
            enabledSummary = "Options développeur Android activées. Ce réglage augmente la surface d'administration mais ne prouve aucune compromission.",
            disabledSummary = "Options développeur Android désactivées.",
            unknownSummary = "État des options développeur non observable.",
            observedAt = snapshot.observedAtEpochMillis
        ),
        evidence(
            id = "android.adb",
            value = snapshot.adbEnabled,
            enabledSummary = "ADB activé. Vérifier que les ordinateurs autorisés et l'usage du débogage sont intentionnels.",
            disabledSummary = "ADB désactivé.",
            unknownSummary = "État ADB non observable.",
            observedAt = snapshot.observedAtEpochMillis
        )
    )

    private fun evidence(
        id: String,
        value: Boolean?,
        enabledSummary: String,
        disabledSummary: String,
        unknownSummary: String,
        observedAt: Long
    ) = SentinelDeviceDiagnostic.Evidence(
        id = id,
        status = when (value) {
            true -> SentinelDeviceDiagnostic.Status.WARNING
            false -> SentinelDeviceDiagnostic.Status.OK
            null -> SentinelDeviceDiagnostic.Status.UNKNOWN
        },
        summary = when (value) {
            true -> enabledSummary
            false -> disabledSummary
            null -> unknownSummary
        },
        observedValue = value?.toString(),
        observedAtEpochMillis = observedAt
    )
}
