package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Audit local des permissions réellement déclarées par l'application et de ses informations de package.
 *
 * The installed package manifest is authoritative. The audit never substitutes a hard-coded
 * subset for the permissions Android actually reports for the installed build.
 */
class SecurityAudit(private val context: Context, private val logger: LocalLogger) {

    fun performAudit(): SecurityAuditResult {
        logger.log(LocalLogger.LogLevel.INFO, "SecurityAudit", "Démarrage de l'audit local")

        val packageInfo = readPackageInfo()
        val permissions = packageInfo.requestedPermissions
            .orEmpty()
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map(::permissionStatus)
            .toList()

        val warnings = permissions
            .filter {
                it.grantModel == PermissionGrantModel.INSTALL_TIME &&
                    !it.granted
            }
            .map {
                "Permission normale déclarée mais indisponible : ${it.name}"
            }

        logger.log(
            LocalLogger.LogLevel.SECURITY,
            "SecurityAudit",
            "Audit terminé: ${permissions.size} permissions déclarées, ${warnings.size} anomalie(s)"
        )

        return SecurityAuditResult(
            permissions = permissions,
            appInfo = appInfo(packageInfo),
            warnings = warnings,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun permissionStatus(permission: String): PermissionStatus =
        PermissionStatus(
            name = permission.substringAfterLast('.'),
            granted =
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED,
            grantModel = permissionGrantModel(permission)
        )

    @Suppress("DEPRECATION")
    private fun permissionGrantModel(
        permission: String
    ): PermissionGrantModel {
        val protectionLevel = runCatching {
            context.packageManager.getPermissionInfo(
                permission,
                0
            ).protectionLevel
        }.getOrNull() ?: return PermissionGrantModel.SYSTEM_CONTROLLED

        return when (
            protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        ) {
            PermissionInfo.PROTECTION_NORMAL ->
                PermissionGrantModel.INSTALL_TIME
            PermissionInfo.PROTECTION_DANGEROUS ->
                PermissionGrantModel.RUNTIME_USER
            else ->
                PermissionGrantModel.SYSTEM_CONTROLLED
        }
    }

    @Suppress("DEPRECATION")
    private fun readPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                )
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
        }

    private fun appInfo(packageInfo: PackageInfo): AppInfo {
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

        return AppInfo(
            versionName = packageInfo.versionName ?: "Inconnue",
            versionCode = versionCode,
            packageName = context.packageName
        )
    }

    data class SecurityAuditResult(
        val permissions: List<PermissionStatus>,
        val appInfo: AppInfo,
        val warnings: List<String>,
        val timestamp: Long
    )

    enum class PermissionGrantModel {
        INSTALL_TIME,
        RUNTIME_USER,
        SYSTEM_CONTROLLED
    }

    data class PermissionStatus(
        val name: String,
        val granted: Boolean,
        val grantModel: PermissionGrantModel
    )

    data class AppInfo(
        val versionName: String,
        val versionCode: Long,
        val packageName: String
    )
}
