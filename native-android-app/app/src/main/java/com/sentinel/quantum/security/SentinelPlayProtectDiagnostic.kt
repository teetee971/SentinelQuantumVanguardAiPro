package com.sentinel.quantum.security

/**
 * Maps a server-verified Google Play Protect verdict into Sentinel evidence.
 *
 * This object does not request or decrypt Play Integrity tokens. Only a verdict
 * already verified by the trusted backend may be passed here.
 */
object SentinelPlayProtectDiagnostic {

    enum class Verdict {
        NO_ISSUES,
        NO_DATA,
        POSSIBLE_RISK,
        MEDIUM_RISK,
        HIGH_RISK,
        UNEVALUATED
    }

    fun evaluate(
        verdict: Verdict?,
        observedAtEpochMillis: Long
    ): SentinelDeviceDiagnostic.Evidence {
        if (verdict == null) {
            return SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "Verdict Play Protect non fourni ou non vérifié côté serveur.",
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        return when (verdict) {
            Verdict.NO_ISSUES -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.OK,
                summary = "Play Protect est actif et n'a signalé aucun problème dans son dernier verdict vérifié. Cela ne garantit pas l'absence de malware.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )

            Verdict.NO_DATA -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "Play Protect est actif mais aucun scan exploitable n'est encore disponible.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )

            Verdict.POSSIBLE_RISK -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.WARNING,
                summary = "Play Protect est désactivé selon le verdict vérifié.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )

            Verdict.MEDIUM_RISK -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.WARNING,
                summary = "Play Protect signale au moins une application potentiellement dangereuse.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )

            Verdict.HIGH_RISK -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.CRITICAL,
                summary = "Play Protect signale au moins une application dangereuse.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )

            Verdict.UNEVALUATED -> SentinelDeviceDiagnostic.Evidence(
                id = "sentinel.malware.play_protect",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "Play Protect n'a pas pu être évalué.",
                observedValue = verdict.name,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }
    }
}
