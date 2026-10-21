package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Read-only local inspector. Does not request privileged permissions, enumerate hidden packages,
 * install APKs, change application state or transmit inventory over the network.
 */
class InstalledAppIntegrityInspector(private val context: Context) {
    fun inspect(packageName: String, now: Long = System.currentTimeMillis()): AppIntegrityReport {
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+").matches(packageName)) {
            return AppIntegrityReport(null, null, listOf(finding(
                "INVALID_PACKAGE", AppIntegrityStatus.UNKNOWN, "Identifiant invalide",
                "Le nom du paquet n'est pas valide.", now
            )))
        }
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return AppIntegrityReport(packageName, null, listOf(finding(
                "PACKAGE_NOT_VISIBLE", AppIntegrityStatus.UNKNOWN, "Application non accessible",
                "Absente ou masquée par les restrictions de visibilité Android.", now
            )))
        } catch (_: SecurityException) {
            return AppIntegrityReport(packageName, null, listOf(finding(
                "PACKAGE_ACCESS_DENIED", AppIntegrityStatus.UNKNOWN, "Accès refusé",
                "Android interdit cette inspection.", now
            )))
        }
        val report = mutableListOf<AppIntegrityFinding>()
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo
            if (signingInfo == null) emptyList() else {
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners?.toList().orEmpty()
                else signingInfo.signingCertificateHistory?.toList().orEmpty()
            }
        } else emptyList()
        if (signatures.isEmpty()) {
            report += finding("SIGNING_UNKNOWN", AppIntegrityStatus.UNKNOWN,
                "Signature non consultable", "Aucun certificat exploitable n'a été obtenu.", now)
        } else {
            val hashes = signatures.map { sig ->
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                    .joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
            }.distinct()
            report += AppIntegrityFinding(
                "SIGNING_SHA256", AppIntegrityStatus.OK, "Certificat disponible",
                "Empreintes publiques seulement ; la présence d'une signature n'est pas un audit de sûreté.",
                AppEvidenceSource.SIGNING_CERTIFICATE,
                hashes.joinToString(", "), now
            )
        }
        val exportedCount = listOf(
            info.activities?.count { it.exported } ?: 0,
            info.services?.count { it.exported } ?: 0,
            info.receivers?.count { it.exported } ?: 0,
            info.providers?.count { it.exported } ?: 0,
        ).sum()
        report += finding("EXPORTED_COUNT", AppIntegrityStatus.UNKNOWN, "Composants accessibles",
            "$exportedCount composant(s) exporté(s) ; leur nécessité et leurs protections demandent une analyse individuelle.",
            now)
        val requested = info.requestedPermissions?.size ?: 0
        report += finding("PERMISSION_COUNT", AppIntegrityStatus.UNKNOWN, "Permissions déclarées",
            "$requested permission(s) déclarée(s) ; aucune anomalie déduite du seul nombre.", now)
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        return AppIntegrityReport(packageName, version, report)
    }

    private fun finding(id: String, status: AppIntegrityStatus, title: String,
                        detail: String, now: Long) =
        AppIntegrityFinding(id, status, title, detail,
            AppEvidenceSource.PACKAGE_MANAGER, null, now)
}
