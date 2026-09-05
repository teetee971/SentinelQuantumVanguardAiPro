package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Audit local des permissions réellement déclarées par l'application et de ses informations de package.
 */
class SecurityAudit(private val context: Context, private val logger: LocalLogger) {

    private val declaredPermissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    fun performAudit(): SecurityAuditResult {
        logger.log(LocalLogger.LogLevel.INFO, "SecurityAudit", "Démarrage de l'audit local")

        val permissions = declaredPermissions.map { permission ->
            PermissionStatus(
                name = permission.substringAfterLast('.'),
                granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            )
        }
        val appInfo = checkAppInfo()
        val warnings = permissions
            .filterNot { it.granted }
            .map { "Permission ${it.name} non accordée" }

        logger.log(LocalLogger.LogLevel.SECURITY, "SecurityAudit", "Audit terminé: ${warnings.size} avertissements")

        return SecurityAuditResult(
            permissions = permissions,
            appInfo = appInfo,
            warnings = warnings,
            timestamp = System.currentTimeMillis()
        )
    }

    @Suppress("DEPRECATION")
    private fun checkAppInfo(): AppInfo {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        return AppInfo(
            versionName = packageInfo.versionName ?: "Unknown",
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

    data class PermissionStatus(
        val name: String,
        val granted: Boolean
    )

    data class AppInfo(
        val versionName: String,
        val versionCode: Long,
        val packageName: String
    )
}
