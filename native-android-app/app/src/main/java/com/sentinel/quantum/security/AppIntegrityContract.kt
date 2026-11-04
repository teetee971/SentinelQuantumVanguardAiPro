package com.sentinel.quantum.security

/**
 * Pure evidence model for App Integrity Pro. A finding is never promoted to OK merely because
 * the package is visible or no detector happened to match.
 */
enum class AppIntegrityStatus { OK, WARN, BAD, UNKNOWN }
enum class AppEvidenceSource { PACKAGE_MANAGER, USER_SELECTED_APK, SIGNING_CERTIFICATE, STATIC_RULE }

data class AppIntegrityFinding(
    val id: String,
    val status: AppIntegrityStatus,
    val title: String,
    val explanation: String,
    val source: AppEvidenceSource,
    val evidence: String?,
    val observedAtMillis: Long,
)

data class AppIntegrityReport(
    val packageName: String?,
    val versionCode: Long?,
    val findings: List<AppIntegrityFinding>,
) {
    val overallStatus: AppIntegrityStatus
        get() = when {
            findings.isEmpty() -> AppIntegrityStatus.UNKNOWN
            findings.any { it.status == AppIntegrityStatus.BAD } -> AppIntegrityStatus.BAD
            findings.any { it.status == AppIntegrityStatus.WARN } -> AppIntegrityStatus.WARN
            findings.any { it.status == AppIntegrityStatus.UNKNOWN } -> AppIntegrityStatus.UNKNOWN
            else -> AppIntegrityStatus.OK
        }
}

/** Certificate mismatch is a review trigger, not proof of malicious tampering:
 * Android signing lineage can legitimately rotate a key. */
object AppIntegrityRules {
    fun compareCertificateHashes(
        expected: String?,
        actual: String?,
        now: Long,
    ): AppIntegrityFinding {
        val x = expected?.trim()?.lowercase()
        val y = actual?.trim()?.lowercase()
        val valid = Regex("^[0-9a-f]{64}$")
        val status = when {
            x == null || y == null || !valid.matches(x) || !valid.matches(y) ->
                AppIntegrityStatus.UNKNOWN
            x == y -> AppIntegrityStatus.OK
            else -> AppIntegrityStatus.WARN
        }
        return AppIntegrityFinding(
            id = "CERTIFICATE_COMPARISON",
            status = status,
            title = "Comparaison du certificat de signature",
            explanation = when (status) {
                AppIntegrityStatus.OK -> "Empreintes identiques. Cela ne garantit pas l'absence de vulnérabilités."
                AppIntegrityStatus.WARN -> "Empreintes différentes : vérifier l'historique de rotation et la provenance."
                else -> "Empreinte absente ou invalide : comparaison impossible."
            },
            source = AppEvidenceSource.SIGNING_CERTIFICATE,
            evidence = when (status) {
                AppIntegrityStatus.OK -> "SHA-256 identique"
                AppIntegrityStatus.WARN -> "SHA-256 différent"
                else -> null
            },
            observedAtMillis = now,
        )
    }
}
