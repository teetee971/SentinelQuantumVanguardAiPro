package com.sentinel.quantum.data

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PermissionRiskLevel {
    HIGH_RISK,
    MEDIUM_RISK,
    LOW_RISK
}

enum class OverallPermissionRisk {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

data class PermissionRiskLabel(
    val permissionName: String,
    val label: String,
    val riskLevel: PermissionRiskLevel
)

data class InstalledAppPermissionProfile(
    val appName: String,
    val packageName: String,
    val iconProvider: (() -> Drawable?)? = null,
    val permissions: List<PermissionRiskLabel>,
    val riskScore: Int,
    val overallRisk: OverallPermissionRisk
)

class AppPermissionAnalyzer(
    private val packageManager: PackageManager
) {
    suspend fun analyzeInstalledApps(): List<InstalledAppPermissionProfile> = withContext(Dispatchers.IO) {
        getInstalledApplications()
            .mapNotNull { applicationInfo ->
                val packageName = applicationInfo.packageName ?: return@mapNotNull null
                val appName = applicationInfo.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: packageName
                val permissions = getRequestedPermissions(packageName)
                    .orEmpty()
                    .map { permissionName ->
                        PermissionRiskLabel(
                            permissionName = permissionName,
                            label = resolvePermissionLabel(permissionName),
                            riskLevel = classifyPermission(permissionName)
                        )
                    }
                    .sortedWith(
                        compareByDescending<PermissionRiskLabel> { scorePermission(it.riskLevel) }
                            .thenBy { it.label }
                    )
                val riskScore = calculateRiskScore(permissions)

                InstalledAppPermissionProfile(
                    appName = appName,
                    packageName = packageName,
                    iconProvider = { loadIcon(packageName) },
                    permissions = permissions,
                    riskScore = riskScore,
                    overallRisk = overallRiskForScore(riskScore)
                )
            }
            .sortedWith(compareByDescending<InstalledAppPermissionProfile> { it.riskScore }.thenBy { it.appName })
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications() =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    @Suppress("DEPRECATION")
    private fun getRequestedPermissions(packageName: String): Array<String>? =
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    @Suppress("DEPRECATION")
    private fun resolvePermissionLabel(permissionName: String): String =
        try {
            packageManager.getPermissionInfo(permissionName, 0)
                .loadLabel(packageManager)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: permissionName
        } catch (_: PackageManager.NameNotFoundException) {
            permissionName
        } catch (_: RuntimeException) {
            permissionName
        }

    private fun loadIcon(packageName: String): Drawable? =
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    companion object {
        private val highRiskPermissions = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )

        private val mediumRiskPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.BLUETOOTH",
            "android.permission.NFC",
            "android.permission.USE_BIOMETRIC",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.POST_NOTIFICATIONS"
        )

        fun classifyPermission(permissionName: String): PermissionRiskLevel =
            when (permissionName) {
                in highRiskPermissions -> PermissionRiskLevel.HIGH_RISK
                in mediumRiskPermissions -> PermissionRiskLevel.MEDIUM_RISK
                else -> PermissionRiskLevel.LOW_RISK
            }

        fun calculateRiskScore(permissions: List<PermissionRiskLabel>): Int =
            permissions.sumOf { scorePermission(it.riskLevel) }

        fun overallRiskForScore(score: Int): OverallPermissionRisk =
            when {
                score >= 12 -> OverallPermissionRisk.CRITICAL
                score >= 6 -> OverallPermissionRisk.HIGH
                score >= 1 -> OverallPermissionRisk.MEDIUM
                else -> OverallPermissionRisk.LOW
            }

        private fun scorePermission(riskLevel: PermissionRiskLevel): Int =
            when (riskLevel) {
                PermissionRiskLevel.HIGH_RISK -> 3
                PermissionRiskLevel.MEDIUM_RISK -> 1
                PermissionRiskLevel.LOW_RISK -> 0
            }
    }
}
