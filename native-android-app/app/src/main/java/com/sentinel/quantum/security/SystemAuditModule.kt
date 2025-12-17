package com.sentinel.quantum.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader

data class SystemAuditResult(
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val selinuxStatus: String,
    val usbDebuggingEnabled: Boolean,
    val unknownSourcesAllowed: Boolean,
    val deviceRooted: Boolean,
    val buildType: String,
    val manufacturerModel: String
)

/**
 * SystemAuditModule - Real Android environment audit
 * 
 * Performs comprehensive security audit of the device:
 * - OS version and security patch level
 * - SELinux status
 * - Debug settings (USB debugging, unknown sources)
 * - Root detection
 * 
 * 100% local, no cloud, no mock data
 * Compliant with Android security best practices
 */
class SystemAuditModule(private val context: Context) {

    fun runAudit(): SystemAuditResult {
        return SystemAuditResult(
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = getSecurityPatch(),
            selinuxStatus = getSelinuxStatus(),
            usbDebuggingEnabled = isUsbDebuggingEnabled(),
            unknownSourcesAllowed = isUnknownSourcesAllowed(),
            deviceRooted = isDeviceRooted(),
            buildType = Build.TYPE ?: "unknown",
            manufacturerModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    private fun getSecurityPatch(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else null
    }

    private fun getSelinuxStatus(): String {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val status = reader.readLine() ?: "unknown"
            reader.close()
            process.waitFor()
            status
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isUnknownSourcesAllowed(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ requires per-app permission
                context.packageManager.canRequestPackageInstalls()
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.INSTALL_NON_MARKET_APPS,
                    0
                ) == 1
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Root detection - checks for common root indicators
     * Note: This is not 100% foolproof but catches common cases
     */
    private fun isDeviceRooted(): Boolean {
        return checkRootBinaries() || checkSuperuserApk() || checkBuildTags()
    }

    private fun checkRootBinaries(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { java.io.File(it).exists() }
    }

    private fun checkSuperuserApk(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )
        return packages.any {
            try {
                context.packageManager.getPackageInfo(it, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS ?: return false
        return tags.contains("test-keys")
    }

    /**
     * Generate human-readable audit report
     */
    fun getAuditReport(): String {
        val result = runAudit()
        return buildString {
            appendLine("=== System Security Audit ===")
            appendLine()
            appendLine("Device: ${result.manufacturerModel}")
            appendLine("Android: ${result.androidVersion} (SDK ${result.sdkInt})")
            appendLine("Security Patch: ${result.securityPatch ?: "N/A"}")
            appendLine("Build Type: ${result.buildType}")
            appendLine()
            appendLine("Security Status:")
            appendLine("- SELinux: ${result.selinuxStatus}")
            appendLine("- USB Debugging: ${if (result.usbDebuggingEnabled) "⚠️ ENABLED" else "✓ Disabled"}")
            appendLine("- Unknown Sources: ${if (result.unknownSourcesAllowed) "⚠️ ALLOWED" else "✓ Blocked"}")
            appendLine("- Root Detected: ${if (result.deviceRooted) "⚠️ YES" else "✓ No"}")
            appendLine()
            appendLine("Risk Level: ${calculateRiskLevel(result)}")
        }
    }

    private fun calculateRiskLevel(result: SystemAuditResult): String {
        var riskScore = 0
        
        if (result.deviceRooted) riskScore += 3
        if (result.usbDebuggingEnabled) riskScore += 2
        if (result.unknownSourcesAllowed) riskScore += 2
        if (result.selinuxStatus != "Enforcing") riskScore += 1
        if (result.securityPatch == null || isSecurityPatchOutdated(result.securityPatch)) riskScore += 1
        
        return when {
            riskScore >= 6 -> "HIGH"
            riskScore >= 3 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun isSecurityPatchOutdated(patch: String?): Boolean {
        if (patch == null) return true
        try {
            // Security patch format: YYYY-MM-DD
            val patchParts = patch.split("-")
            if (patchParts.size != 3) return true
            
            val patchYear = patchParts[0].toInt()
            val patchMonth = patchParts[1].toInt()
            
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
            
            // Consider outdated if more than 6 months old
            val monthsOld = (currentYear - patchYear) * 12 + (currentMonth - patchMonth)
            return monthsOld > 6
        } catch (e: Exception) {
            return true
        }
    }
}
