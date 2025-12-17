package com.sentinel.quantum.security

import android.os.Build
import java.io.File

data class RiskEnvironmentResult(
    val rootIndicators: List<String>,
    val emulatorIndicators: List<String>,
    val hookingIndicators: List<String>,
    val overallRiskLevel: RiskLevel
)

enum class RiskLevel {
    LOW,      // 0-2 indicators
    MEDIUM,   // 3-5 indicators
    HIGH,     // 6-9 indicators
    CRITICAL  // 10+ indicators
}

/**
 * RiskEnvironmentDetector (MODULE D) - Detect risky environment indicators
 * 
 * Passive detection of potentially unsafe environments:
 * - Root indicators (binaries, packages)
 * - Emulator indicators (build fingerprints, hardware)
 * - Hooking/instrumentation indicators (Frida, Xposed, etc.)
 * 
 * IMPORTANT: This is DETECTION ONLY - no exploitation, no bypass, no attack
 * Purpose: Security audit and risk assessment for defensive purposes
 * 
 * Compliance:
 * - 100% passive analysis
 * - No system modification
 * - No privilege escalation attempts
 * - ANSSI audit-ready
 */
class RiskEnvironmentDetector {

    fun analyze(): RiskEnvironmentResult {
        val rootIndicators = detectRoot()
        val emulatorIndicators = detectEmulator()
        val hookingIndicators = detectHooking()
        
        val totalIndicators = rootIndicators.size + emulatorIndicators.size + hookingIndicators.size
        val riskLevel = calculateRiskLevel(totalIndicators)
        
        return RiskEnvironmentResult(
            rootIndicators = rootIndicators,
            emulatorIndicators = emulatorIndicators,
            hookingIndicators = hookingIndicators,
            overallRiskLevel = riskLevel
        )
    }

    /**
     * Root detection - passive file system checks
     * No exploitation, just detection for security awareness
     */
    private fun detectRoot(): List<String> {
        val indicators = mutableListOf<String>()
        
        // Common root binary paths
        val rootPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/xbin/daemonsu",
            "/system/etc/init.d/99SuperSUDaemon"
        )
        
        rootPaths.forEach { path ->
            if (File(path).exists()) {
                indicators.add("Root binary found: $path")
            }
        }
        
        // Check for test-keys (custom ROM indicator)
        if (Build.TAGS?.contains("test-keys") == true) {
            indicators.add("Build signed with test-keys (custom ROM)")
        }
        
        return indicators
    }

    /**
     * Emulator detection - build property analysis
     * Helps identify if app is running in virtualized environment
     */
    private fun detectEmulator(): List<String> {
        val indicators = mutableListOf<String>()
        
        // Build fingerprint checks
        val fingerprint = Build.FINGERPRINT
        if (fingerprint.contains("generic", ignoreCase = true)) {
            indicators.add("Generic fingerprint detected")
        }
        if (fingerprint.contains("unknown", ignoreCase = true)) {
            indicators.add("Unknown fingerprint")
        }
        
        // Model checks
        val model = Build.MODEL
        if (model.contains("google_sdk", ignoreCase = true)) {
            indicators.add("Google SDK model detected")
        }
        if (model.contains("Emulator", ignoreCase = true)) {
            indicators.add("Emulator model detected")
        }
        if (model.contains("Android SDK built for x86", ignoreCase = true)) {
            indicators.add("Android SDK x86 detected")
        }
        
        // Manufacturer checks
        val manufacturer = Build.MANUFACTURER
        if (manufacturer.contains("Genymotion", ignoreCase = true)) {
            indicators.add("Genymotion emulator detected")
        }
        
        // Device and hardware checks
        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) {
            indicators.add("Generic brand/device combination")
        }
        
        if (Build.PRODUCT == "google_sdk") {
            indicators.add("Google SDK product detected")
        }
        
        // Hardware checks
        val hardware = Build.HARDWARE
        if (hardware.contains("goldfish", ignoreCase = true)) {
            indicators.add("Goldfish hardware (emulator)")
        }
        if (hardware.contains("ranchu", ignoreCase = true)) {
            indicators.add("Ranchu hardware (emulator)")
        }
        
        // Radio version check (emulator often returns null)
        if (Build.getRadioVersion().isEmpty()) {
            indicators.add("Empty radio version (possible emulator)")
        }
        
        return indicators
    }

    /**
     * Hooking/instrumentation detection
     * Detects common runtime manipulation frameworks
     * Note: Detection only - no anti-debugging, no tampering
     */
    private fun detectHooking(): List<String> {
        val indicators = mutableListOf<String>()
        
        // Frida detection
        val fridaPaths = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida",
            "/data/local/tmp/re.frida.server"
        )
        fridaPaths.forEach { path ->
            if (File(path).exists()) {
                indicators.add("Frida artifact detected: $path")
            }
        }
        
        // Check for Frida ports (passive check only)
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                if (content.contains("frida", ignoreCase = true)) {
                    indicators.add("Frida detected in process memory maps")
                }
            }
        } catch (e: Exception) {
            // Silent fail - read protection is normal
        }
        
        // Substrate/Cydia Substrate detection
        val substratePaths = listOf(
            "/data/local/tmp/substrate",
            "/system/lib/libsubstrate.so",
            "/system/lib64/libsubstrate.so"
        )
        substratePaths.forEach { path ->
            if (File(path).exists()) {
                indicators.add("Substrate artifact detected: $path")
            }
        }
        
        // Xposed detection
        val xposedIndicators = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/bin/app_process_xposed",
            "/system/xbin/xposed"
        )
        xposedIndicators.forEach { path ->
            if (File(path).exists()) {
                indicators.add("Xposed artifact detected: $path")
            }
        }
        
        // Check for known hooking libraries
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                val suspiciousLibs = listOf("xposed", "substrate", "frida")
                suspiciousLibs.forEach { lib ->
                    if (content.contains(lib, ignoreCase = true)) {
                        indicators.add("Suspicious library in memory: $lib")
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail
        }
        
        // Magisk detection (modern root manager)
        val magiskPaths = listOf(
            "/sbin/.magisk",
            "/system/xbin/magisk",
            "/data/adb/magisk"
        )
        magiskPaths.forEach { path ->
            if (File(path).exists()) {
                indicators.add("Magisk artifact detected: $path")
            }
        }
        
        return indicators
    }

    private fun calculateRiskLevel(totalIndicators: Int): RiskLevel {
        return when {
            totalIndicators >= 10 -> RiskLevel.CRITICAL
            totalIndicators >= 6 -> RiskLevel.HIGH
            totalIndicators >= 3 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    /**
     * Generate human-readable risk environment report
     */
    fun getRiskReport(): String {
        val result = analyze()
        return buildString {
            appendLine("=== Risk Environment Analysis ===")
            appendLine()
            appendLine("Overall Risk Level: ${result.overallRiskLevel.name}")
            appendLine()
            
            if (result.rootIndicators.isNotEmpty()) {
                appendLine("⚠️ Root Indicators (${result.rootIndicators.size}):")
                result.rootIndicators.forEach { indicator ->
                    appendLine("  • $indicator")
                }
                appendLine()
            }
            
            if (result.emulatorIndicators.isNotEmpty()) {
                appendLine("⚠️ Emulator Indicators (${result.emulatorIndicators.size}):")
                result.emulatorIndicators.forEach { indicator ->
                    appendLine("  • $indicator")
                }
                appendLine()
            }
            
            if (result.hookingIndicators.isNotEmpty()) {
                appendLine("⚠️ Hooking/Instrumentation Indicators (${result.hookingIndicators.size}):")
                result.hookingIndicators.forEach { indicator ->
                    appendLine("  • $indicator")
                }
                appendLine()
            }
            
            if (result.rootIndicators.isEmpty() && 
                result.emulatorIndicators.isEmpty() && 
                result.hookingIndicators.isEmpty()) {
                appendLine("✓ No risk indicators detected")
                appendLine()
            }
            
            appendLine("Security Assessment:")
            when (result.overallRiskLevel) {
                RiskLevel.LOW -> {
                    appendLine("✓ Environment appears safe for normal operations")
                }
                RiskLevel.MEDIUM -> {
                    appendLine("⚠️ Some risk indicators present - proceed with caution")
                    appendLine("💡 Recommendation: Review detected indicators")
                }
                RiskLevel.HIGH -> {
                    appendLine("⚠️ Multiple risk indicators detected")
                    appendLine("💡 Recommendation: Avoid sensitive operations")
                }
                RiskLevel.CRITICAL -> {
                    appendLine("🚨 Critical risk level - environment is heavily modified")
                    appendLine("💡 Recommendation: Do not perform sensitive operations")
                }
            }
            
            appendLine()
            appendLine("Disclaimer:")
            appendLine("This is passive detection only - no exploitation or bypass attempts")
            appendLine("Results are indicative and may include false positives")
            appendLine("Intended for security audit and risk assessment purposes")
        }
    }

    /**
     * Check if environment is considered safe
     */
    fun isSafeEnvironment(): Boolean {
        val result = analyze()
        return result.overallRiskLevel == RiskLevel.LOW
    }

    /**
     * Get total indicator count
     */
    fun getTotalIndicatorCount(): Int {
        val result = analyze()
        return result.rootIndicators.size + 
               result.emulatorIndicators.size + 
               result.hookingIndicators.size
    }
}
