package com.sentinel.quantum.security

/** Pure, platform-independent two-version comparison. Unknown metadata never becomes OK. */
data class ApkEvidence(
    val packageName: String?,
    val versionCode: Long?,
    val fileSha256: String?,
    val signerSha256: Set<String>?,
    val signatureVerified: Boolean? = null,
)

data class ApkComparison(
    val findings: List<AppIntegrityFinding>,
) {
    val hasReviewRequired: Boolean get() = findings.any { it.status == AppIntegrityStatus.WARN || it.status == AppIntegrityStatus.BAD }
    val hasUnknownEvidence: Boolean get() = findings.any { it.status == AppIntegrityStatus.UNKNOWN }
    val isFullyVerified: Boolean get() = findings.isNotEmpty() && findings.all { it.status == AppIntegrityStatus.OK }
}

object ApkComparisonEngine {
    private val sha256 = Regex("^[a-fA-F0-9]{64}$")
    fun compare(before: ApkEvidence, after: ApkEvidence, now: Long): ApkComparison {
        val results = mutableListOf<AppIntegrityFinding>()
        fun finding(id: String, status: AppIntegrityStatus, title: String, explanation: String) {
            results += AppIntegrityFinding(
                id, status, title, explanation, AppEvidenceSource.USER_SELECTED_APK, null, now
            )
        }
        val beforePackage = before.packageName?.takeIf { it.isNotBlank() }
        val afterPackage = after.packageName?.takeIf { it.isNotBlank() }
        finding("PACKAGE_ID", when {
            beforePackage == null || afterPackage == null -> AppIntegrityStatus.UNKNOWN
            beforePackage == afterPackage -> AppIntegrityStatus.OK
            else -> AppIntegrityStatus.WARN
        }, "Identifiant de paquet", "Un changement de package exige une revue de provenance.")

        finding("VERSION_CODE", when {
            before.versionCode == null || after.versionCode == null -> AppIntegrityStatus.UNKNOWN
            after.versionCode < before.versionCode -> AppIntegrityStatus.WARN
            else -> AppIntegrityStatus.OK
        }, "Numéro de build", "Une régression de version peut être intentionnelle, à vérifier.")

        val left = before.fileSha256?.takeIf { sha256.matches(it) }
        val right = after.fileSha256?.takeIf { sha256.matches(it) }
        finding("APK_CONTENT_SHA256", when {
            left == null || right == null -> AppIntegrityStatus.UNKNOWN
            left.equals(right, ignoreCase = true) -> AppIntegrityStatus.OK
            else -> AppIntegrityStatus.WARN
        }, "Contenu APK", "Des empreintes différentes indiquent des octets différents, pas une attaque prouvée.")

        fun signerSet(input: Set<String>?): Set<String>? =
            input?.takeIf { it.isNotEmpty() && it.all(sha256::matches) }
                ?.map { it.lowercase() }?.toSet()
        val prev = signerSet(before.signerSha256)
        val next = signerSet(after.signerSha256)
        finding("SIGNER_SET", when {
            prev == null || next == null -> AppIntegrityStatus.UNKNOWN
            prev == next -> AppIntegrityStatus.OK
            else -> AppIntegrityStatus.WARN
        }, "Certificats signataires", "Vérifier les rotations légitimes de signature avant de qualifier une divergence.")
        val signatureStatus = when {
            before.signatureVerified == false || after.signatureVerified == false -> AppIntegrityStatus.BAD
            before.signatureVerified == true && after.signatureVerified == true -> AppIntegrityStatus.OK
            else -> AppIntegrityStatus.UNKNOWN
        }
        finding("CRYPTOGRAPHIC_SIGNATURE", signatureStatus, "Validité cryptographique des APK",
            when (signatureStatus) {
                AppIntegrityStatus.BAD -> "Au moins un APK présente une signature cryptographique invalide. Ne pas installer ce fichier."
                AppIntegrityStatus.OK -> "Les signatures sont cryptographiquement valides. Cela ne garantit ni une provenance fiable ni l’absence de code malveillant."
                else -> "Validité cryptographique non démontrée pour au moins un APK ; résultat indéterminé."
            })
        return ApkComparison(results)
    }
}
