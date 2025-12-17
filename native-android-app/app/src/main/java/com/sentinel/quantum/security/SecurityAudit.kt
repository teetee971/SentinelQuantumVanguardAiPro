package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * SecurityAudit - Audit de sécurité local
 * Analyse les permissions et l'état de sécurité de l'appareil
 */
class SecurityAudit(private val context: Context, private val logger: LocalLogger) {
    
    /**
     * Effectue un audit complet de sécurité
     */
    fun performAudit(): SecurityAuditResult {
        logger.log(LocalLogger.LogLevel.INFO, "SecurityAudit", "Démarrage de l'audit de sécurité")
        
        val permissions = checkPermissions()
        val appInfo = checkAppInfo()
        val warnings = mutableListOf<String>()
        
        // Vérifications de sécurité
        if (!permissions.phoneStateGranted) {
            warnings.add("Permission READ_PHONE_STATE non accordée")
        }
        
        if (!permissions.callLogGranted) {
            warnings.add("Permission READ_CALL_LOG non accordée - fonctionnalité de détection SPAM limitée")
        }
        
        logger.log(LocalLogger.LogLevel.SECURITY, "SecurityAudit", "Audit terminé: ${warnings.size} avertissements")
        
        return SecurityAuditResult(
            permissions = permissions,
            appInfo = appInfo,
            warnings = warnings,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun checkPermissions(): PermissionStatus {
        val phoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        val callLog = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        
        return PermissionStatus(
            phoneStateGranted = phoneState,
            callLogGranted = callLog
        )
    }
    
    private fun checkAppInfo(): AppInfo {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        
        return AppInfo(
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = packageInfo.longVersionCode,
            packageName = context.packageName
        )
    }
    
    data class SecurityAuditResult(
        val permissions: PermissionStatus,
        val appInfo: AppInfo,
        val warnings: List<String>,
        val timestamp: Long
    )
    
    data class PermissionStatus(
        val phoneStateGranted: Boolean,
        val callLogGranted: Boolean
    )
    
    data class AppInfo(
        val versionName: String,
        val versionCode: Long,
        val packageName: String
    )
}
