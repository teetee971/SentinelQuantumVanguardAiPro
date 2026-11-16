package com.sentinel.quantum.security

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure interpretation layer. It never claims update availability from patch age alone. */
object SentinelSystemDiagnosticPolicy {
    private const val PATCH_WARNING_DAYS = 120L
    private const val PATCH_CRITICAL_DAYS = 365L

    fun evaluate(snapshot: SentinelSystemSnapshot, today: LocalDate): SentinelDeviceDiagnostic.Report {
        val observedAt = snapshot.observedAtEpochMillis
        val evidence = mutableListOf<SentinelDeviceDiagnostic.Evidence>()

        evidence += SentinelDeviceDiagnostic.Evidence(
            id = "android.sdk",
            status = if (snapshot.sdkInt > 0) SentinelDeviceDiagnostic.Status.OK else SentinelDeviceDiagnostic.Status.UNKNOWN,
            summary = "Version API Android observée localement.",
            observedValue = snapshot.sdkInt.takeIf { it > 0 }?.toString(),
            observedAtEpochMillis = observedAt
        )
        evidence += textEvidence("android.release", "Version Android observée localement.", snapshot.release, observedAt)
        evidence += textEvidence("android.build", "Identifiant de build Android observé localement.", snapshot.buildDisplay, observedAt)
        evidence += patchEvidence(snapshot.securityPatch, observedAt, today)

        return SentinelDeviceDiagnostic.Report(evidence)
    }

    internal fun patchEvidence(
        rawPatch: String,
        observedAtEpochMillis: Long,
        today: LocalDate
    ): SentinelDeviceDiagnostic.Evidence {
        if (rawPatch.isBlank()) return SentinelDeviceDiagnostic.Evidence(
            "android.security_patch",
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            "Niveau de correctif de sécurité non fourni par Android.",
            null,
            observedAtEpochMillis
        )

        val patchDate = runCatching { LocalDate.parse(rawPatch) }.getOrNull()
            ?: return SentinelDeviceDiagnostic.Evidence(
                "android.security_patch",
                SentinelDeviceDiagnostic.Status.UNKNOWN,
                "Niveau de correctif de sécurité Android illisible.",
                rawPatch,
                observedAtEpochMillis
            )

        val ageDays = ChronoUnit.DAYS.between(patchDate, today)
        val status = when {
            ageDays < 0L -> SentinelDeviceDiagnostic.Status.UNKNOWN
            ageDays >= PATCH_CRITICAL_DAYS -> SentinelDeviceDiagnostic.Status.CRITICAL
            ageDays >= PATCH_WARNING_DAYS -> SentinelDeviceDiagnostic.Status.WARNING
            else -> SentinelDeviceDiagnostic.Status.OK
        }
        val summary = when (status) {
            SentinelDeviceDiagnostic.Status.OK -> "Correctif de sécurité Android récent selon la politique Sentinel."
            SentinelDeviceDiagnostic.Status.WARNING -> "Correctif de sécurité Android ancien ; vérifier les mises à jour proposées par le constructeur."
            SentinelDeviceDiagnostic.Status.CRITICAL -> "Correctif de sécurité Android très ancien ; vérifier rapidement les mises à jour constructeur."
            else -> "Date du correctif de sécurité Android incohérente."
        }
        return SentinelDeviceDiagnostic.Evidence(
            "android.security_patch", status, summary, rawPatch, observedAtEpochMillis
        )
    }

    private fun textEvidence(
        id: String,
        summary: String,
        value: String,
        observedAtEpochMillis: Long
    ) = SentinelDeviceDiagnostic.Evidence(
        id = id,
        status = if (value.isBlank()) SentinelDeviceDiagnostic.Status.UNKNOWN else SentinelDeviceDiagnostic.Status.OK,
        summary = if (value.isBlank()) "$summary Valeur indisponible." else summary,
        observedValue = value.takeIf { it.isNotBlank() },
        observedAtEpochMillis = observedAtEpochMillis
    )
}
