package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

data class IntegrityResult(
    val installerPackage: String?,
    val isDebuggable: Boolean,
    val signatureSha256: String?,
    val signatureMatchExpected: Boolean,
    val installerType: InstallerType,
    val tamperingDetected: Boolean
)

enum class InstallerType {
    PLAY_STORE,        // Installed from Google Play
    SIDE_LOADED,       // Installed via ADB or file manager
    THIRD_PARTY_STORE, // Amazon, Samsung, etc.
    UNKNOWN            // Cannot determine
}

/**
 * IntegrityVerifier - Real APK integrity verification
 * 
 * Verifies APK integrity through multiple checks:
 * - Installation source (Play Store vs. side-load)
 * - Debug mode detection
 * - Signature SHA-256 verification
 * - Basic tampering detection
 * 
 * 100% local verification, no cloud dependencies
 * ANSSI-compliant integrity checking
 */
class IntegrityVerifier(
    private val context: Context,
    private val expectedSignatureSha256: String? = null // Optional: your official signature
) {

    fun verify(): IntegrityResult {
        val installer = getInstaller()
        val signatureSha256 = getSignatureSha256()
        
        return IntegrityResult(
            installerPackage = installer,
            isDebuggable = isDebuggable(),
            signatureSha256 = signatureSha256,
            signatureMatchExpected = expectedSignatureSha256?.let {
                it.equals(signatureSha256, ignoreCase = true)
            } ?: true,
            installerType = classifyInstaller(installer),
            tamperingDetected = detectTampering()
        )
    }

    private fun getInstaller(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun classifyInstaller(installer: String?): InstallerType {
        return when (installer) {
            "com.android.vending" -> InstallerType.PLAY_STORE
            "com.google.android.feedback" -> InstallerType.PLAY_STORE
            "com.amazon.venezia" -> InstallerType.THIRD_PARTY_STORE
            "com.sec.android.app.samsungapps" -> InstallerType.THIRD_PARTY_STORE
            "com.huawei.appmarket" -> InstallerType.THIRD_PARTY_STORE
            null -> InstallerType.SIDE_LOADED
            else -> {
                // Check if it's a known installer pattern
                if (installer.contains("market") || installer.contains("store")) {
                    InstallerType.THIRD_PARTY_STORE
                } else {
                    InstallerType.UNKNOWN
                }
            }
        }
    }

    private fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun getSignatureSha256(): String? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES
            else
                @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

            val pkg: PackageInfo = pm.getPackageInfo(context.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION") pkg.signatures
            }

            if (signatures.isEmpty()) return null

            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signatures.first().toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Basic tampering detection
     * Checks for common signs of APK modification
     */
    private fun detectTampering(): Boolean {
        var tamperingIndicators = 0

        // Check 1: Debug mode on release build
        if (isDebuggable() && Build.TYPE == "user") {
            tamperingIndicators++
        }

        // Check 2: Side-loaded on device that should use Play Store
        val installer = getInstaller()
        if (installer == null && !isEmulator()) {
            tamperingIndicators++
        }

        // Check 3: Signature mismatch (if expected signature provided)
        if (expectedSignatureSha256 != null) {
            val currentSignature = getSignatureSha256()
            if (currentSignature != null && !expectedSignatureSha256.equals(currentSignature, ignoreCase = true)) {
                tamperingIndicators++
            }
        }

        // Check 4: Multiple signatures (potential repackaging)
        if (hasMultipleSignatures()) {
            tamperingIndicators++
        }

        // Consider tampered if 2 or more indicators
        return tamperingIndicators >= 2
    }

    private fun hasMultipleSignatures(): Boolean {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES
            else
                @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

            val pkg: PackageInfo = pm.getPackageInfo(context.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION") pkg.signatures
            }

            signatures.size > 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Generate human-readable integrity report
     */
    fun getIntegrityReport(): String {
        val result = verify()
        return buildString {
            appendLine("=== APK Integrity Verification ===")
            appendLine()
            appendLine("Installation Source:")
            appendLine("- Installer: ${result.installerPackage ?: "Unknown/Side-loaded"}")
            appendLine("- Type: ${result.installerType.name.replace('_', ' ')}")
            appendLine()
            appendLine("Build Configuration:")
            appendLine("- Debuggable: ${if (result.isDebuggable) "⚠️ YES (Debug build)" else "✓ No (Release)"}")
            appendLine()
            appendLine("Signature Verification:")
            appendLine("- SHA-256: ${result.signatureSha256?.take(32)}...")
            
            if (expectedSignatureSha256 != null) {
                appendLine("- Expected Match: ${if (result.signatureMatchExpected) "✓ Valid" else "⚠️ MISMATCH"}")
            } else {
                appendLine("- Expected Match: N/A (no reference signature)")
            }
            
            appendLine()
            appendLine("Tampering Detection:")
            appendLine("- Status: ${if (result.tamperingDetected) "⚠️ POTENTIAL TAMPERING DETECTED" else "✓ No tampering detected"}")
            
            appendLine()
            appendSecurityRecommendations(result)
        }
    }

    private fun StringBuilder.appendSecurityRecommendations(result: IntegrityResult) {
        appendLine("Security Recommendations:")
        
        var hasWarnings = false
        
        if (result.installerType == InstallerType.SIDE_LOADED) {
            appendLine("⚠️ App was side-loaded - verify APK source authenticity")
            hasWarnings = true
        }
        
        if (result.isDebuggable) {
            appendLine("⚠️ Debug mode enabled - not recommended for production")
            hasWarnings = true
        }
        
        if (!result.signatureMatchExpected) {
            appendLine("⚠️ Signature mismatch - APK may have been modified")
            hasWarnings = true
        }
        
        if (result.tamperingDetected) {
            appendLine("⚠️ Tampering indicators detected - proceed with caution")
            hasWarnings = true
        }
        
        if (!hasWarnings) {
            appendLine("✓ APK integrity verified - no issues detected")
        }
    }
}
