package com.sentinel.quantum.security

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class SecurityScore(
    val score: Int,
    val level: SecurityLevel,
    val breakdown: ScoreBreakdown
)

data class ScoreBreakdown(
    val systemScore: Int,
    val networkScore: Int,
    val permissionsScore: Int,
    val integrityScore: Int
)

enum class SecurityLevel {
    SECURE,    // 85-100
    MODERATE,  // 65-84
    WEAK,      // 40-64
    CRITICAL   // 0-39
}

/**
 * SecurityReportBuilder - Comprehensive security report aggregator
 * 
 * Aggregates all security modules into unified report:
 * - System audit (OS, patches, SELinux, root)
 * - Network security (WiFi, VPN, DNS)
 * - Permissions analysis (dangerous permissions)
 * - APK integrity (signature, tampering)
 * 
 * Generates:
 * - JSON report (machine-readable)
 * - Security score (0-100)
 * - Risk level classification
 * - Human-readable summary
 * 
 * 100% local processing, ANSSI/audit-ready
 */
class SecurityReportBuilder {

    /**
     * Build comprehensive JSON security report
     */
    fun buildJson(
        systemAudit: SystemAuditResult,
        networkAudit: NetworkSecurityResult,
        permissions: List<PermissionFinding>,
        integrity: IntegrityResult
    ): JSONObject {
        val root = JSONObject()

        // Metadata
        root.put("metadata", JSONObject().apply {
            put("reportType", "Sentinel Security Audit")
            put("version", "1.0.0")
            put("timestamp", getCurrentTimestamp())
            put("reportId", generateReportId())
        })

        // System Security
        root.put("system", JSONObject().apply {
            put("androidVersion", systemAudit.androidVersion)
            put("sdk", systemAudit.sdkInt)
            put("securityPatch", systemAudit.securityPatch ?: "unknown")
            put("selinuxStatus", systemAudit.selinuxStatus)
            put("usbDebuggingEnabled", systemAudit.usbDebuggingEnabled)
            put("unknownSourcesAllowed", systemAudit.unknownSourcesAllowed)
            put("deviceRooted", systemAudit.deviceRooted)
            put("buildType", systemAudit.buildType)
            put("device", systemAudit.manufacturerModel)
        })

        // Network Security
        root.put("network", JSONObject().apply {
            put("connectionType", networkAudit.connectionType)
            put("vpnActive", networkAudit.isVpnActive)
            
            if (networkAudit.wifiSSID != null) {
                put("wifi", JSONObject().apply {
                    put("ssid", networkAudit.wifiSSID)
                    put("securityType", networkAudit.wifiSecurityType ?: "unknown")
                    put("isSecure", networkAudit.isWifiSecure ?: false)
                })
            }
            
            put("proxyEnabled", networkAudit.proxyEnabled)
            put("localIp", networkAudit.localIpAddress ?: "unknown")
            
            if (networkAudit.dnsServers.isNotEmpty()) {
                put("dnsServers", JSONArray(networkAudit.dnsServers))
            }
            
            put("interfacesCount", networkAudit.networkInterfaces.size)
        })

        // APK Integrity
        root.put("integrity", JSONObject().apply {
            put("installer", integrity.installerPackage ?: "unknown")
            put("installerType", integrity.installerType.name)
            put("debuggable", integrity.isDebuggable)
            put("signature", integrity.signatureSha256 ?: "unknown")
            put("signatureValid", integrity.signatureMatchExpected)
            put("tamperingDetected", integrity.tamperingDetected)
        })

        // Permissions
        val permissionsArray = JSONArray()
        permissions.forEach { finding ->
            permissionsArray.put(JSONObject().apply {
                put("name", finding.permission)
                put("granted", finding.granted)
                put("dangerous", finding.dangerous)
                put("description", finding.description)
            })
        }
        root.put("permissions", JSONObject().apply {
            put("total", permissions.size)
            put("granted", permissions.count { it.granted })
            put("dangerous", permissions.count { it.dangerous })
            put("dangerousGranted", permissions.count { it.dangerous && it.granted })
            put("list", permissionsArray)
        })

        // Security Score
        val score = computeScore(systemAudit, networkAudit, permissions, integrity)
        root.put("score", JSONObject().apply {
            put("value", score.score)
            put("level", score.level.name)
            put("breakdown", JSONObject().apply {
                put("system", score.breakdown.systemScore)
                put("network", score.breakdown.networkScore)
                put("permissions", score.breakdown.permissionsScore)
                put("integrity", score.breakdown.integrityScore)
            })
        })

        // Recommendations
        root.put("recommendations", JSONArray().apply {
            getRecommendations(systemAudit, networkAudit, permissions, integrity).forEach {
                put(it)
            }
        })

        return root
    }

    /**
     * Compute overall security score (0-100)
     */
    private fun computeScore(
        systemAudit: SystemAuditResult,
        networkAudit: NetworkSecurityResult,
        permissions: List<PermissionFinding>,
        integrity: IntegrityResult
    ): SecurityScore {
        // System score (0-25 points)
        var systemScore = 25
        if (systemAudit.deviceRooted) systemScore -= 10
        if (systemAudit.usbDebuggingEnabled) systemScore -= 5
        if (systemAudit.unknownSourcesAllowed) systemScore -= 5
        if (systemAudit.selinuxStatus.equals("Permissive", true)) systemScore -= 5
        systemScore = systemScore.coerceIn(0, 25)

        // Network score (0-25 points)
        var networkScore = 25
        if (networkAudit.isWifiSecure == false) networkScore -= 10
        if (!networkAudit.isVpnActive && networkAudit.connectionType == "WiFi") networkScore -= 5
        if (networkAudit.wifiSecurityType?.contains("WEP") == true || 
            networkAudit.wifiSecurityType == "Open") networkScore -= 10
        networkScore = networkScore.coerceIn(0, 25)

        // Integrity score (0-30 points)
        var integrityScore = 30
        if (integrity.isDebuggable) integrityScore -= 10
        if (!integrity.signatureMatchExpected) integrityScore -= 15
        if (integrity.tamperingDetected) integrityScore -= 10
        if (integrity.installerType == InstallerType.SIDE_LOADED) integrityScore -= 5
        integrityScore = integrityScore.coerceIn(0, 30)

        // Permissions score (0-20 points)
        var permissionsScore = 20
        val dangerousGranted = permissions.count { it.dangerous && it.granted }
        permissionsScore -= dangerousGranted * 2
        permissionsScore = permissionsScore.coerceIn(0, 20)

        val totalScore = systemScore + networkScore + integrityScore + permissionsScore
        val level = when {
            totalScore >= 85 -> SecurityLevel.SECURE
            totalScore >= 65 -> SecurityLevel.MODERATE
            totalScore >= 40 -> SecurityLevel.WEAK
            else -> SecurityLevel.CRITICAL
        }

        return SecurityScore(
            score = totalScore,
            level = level,
            breakdown = ScoreBreakdown(
                systemScore = systemScore,
                networkScore = networkScore,
                permissionsScore = permissionsScore,
                integrityScore = integrityScore
            )
        )
    }

    /**
     * Generate actionable security recommendations
     */
    private fun getRecommendations(
        systemAudit: SystemAuditResult,
        networkAudit: NetworkSecurityResult,
        permissions: List<PermissionFinding>,
        integrity: IntegrityResult
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // System recommendations
        if (systemAudit.deviceRooted) {
            recommendations.add("CRITICAL: Device is rooted - sensitive operations may be compromised")
        }
        if (systemAudit.usbDebuggingEnabled) {
            recommendations.add("HIGH: Disable USB debugging in developer options")
        }
        if (systemAudit.unknownSourcesAllowed) {
            recommendations.add("MEDIUM: Disable installation from unknown sources")
        }
        if (systemAudit.selinuxStatus != "Enforcing") {
            recommendations.add("HIGH: SELinux is not in enforcing mode")
        }

        // Network recommendations
        if (networkAudit.isWifiSecure == false) {
            recommendations.add("HIGH: Connected to insecure WiFi - avoid sensitive transactions")
        }
        if (!networkAudit.isVpnActive && networkAudit.connectionType == "WiFi") {
            recommendations.add("MEDIUM: Consider using VPN on WiFi networks")
        }
        if (networkAudit.wifiSecurityType == "Open" || networkAudit.wifiSecurityType?.contains("WEP") == true) {
            recommendations.add("CRITICAL: WiFi has weak/no encryption - use VPN immediately")
        }

        // Integrity recommendations
        if (integrity.isDebuggable) {
            recommendations.add("MEDIUM: App is in debug mode - not recommended for production")
        }
        if (!integrity.signatureMatchExpected) {
            recommendations.add("CRITICAL: APK signature mismatch - app may have been modified")
        }
        if (integrity.tamperingDetected) {
            recommendations.add("CRITICAL: Tampering detected - verify APK authenticity")
        }
        if (integrity.installerType == InstallerType.SIDE_LOADED) {
            recommendations.add("MEDIUM: App was side-loaded - verify source authenticity")
        }

        // Permissions recommendations
        val dangerousGranted = permissions.filter { it.dangerous && it.granted }
        if (dangerousGranted.size > 5) {
            recommendations.add("MEDIUM: ${dangerousGranted.size} dangerous permissions granted - review necessity")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("SECURE: No major security issues detected")
        }

        return recommendations
    }

    /**
     * Generate human-readable comprehensive report
     */
    fun buildHumanReadableReport(
        systemAudit: SystemAuditResult,
        networkAudit: NetworkSecurityResult,
        permissions: List<PermissionFinding>,
        integrity: IntegrityResult
    ): String {
        val score = computeScore(systemAudit, networkAudit, permissions, integrity)
        
        return buildString {
            appendLine("╔════════════════════════════════════════════════════╗")
            appendLine("║   SENTINEL COMPREHENSIVE SECURITY REPORT          ║")
            appendLine("╚════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("Generated: ${getCurrentTimestamp()}")
            appendLine()
            
            // Overall Score
            appendLine("═══ OVERALL SECURITY SCORE ═══")
            appendLine()
            appendLine("Score: ${score.score}/100")
            appendLine("Level: ${score.level.name}")
            appendLine()
            appendLine("Breakdown:")
            appendLine("  System:      ${score.breakdown.systemScore}/25")
            appendLine("  Network:     ${score.breakdown.networkScore}/25")
            appendLine("  Integrity:   ${score.breakdown.integrityScore}/30")
            appendLine("  Permissions: ${score.breakdown.permissionsScore}/20")
            appendLine()
            
            // System Summary
            appendLine("═══ SYSTEM SECURITY ═══")
            appendLine()
            appendLine("Device: ${systemAudit.manufacturerModel}")
            appendLine("Android: ${systemAudit.androidVersion} (SDK ${systemAudit.sdkInt})")
            appendLine("Security Patch: ${systemAudit.securityPatch ?: "N/A"}")
            appendLine("SELinux: ${systemAudit.selinuxStatus}")
            appendLine("Root: ${if (systemAudit.deviceRooted) "⚠️ DETECTED" else "✓ Not detected"}")
            appendLine("USB Debug: ${if (systemAudit.usbDebuggingEnabled) "⚠️ ENABLED" else "✓ Disabled"}")
            appendLine()
            
            // Network Summary
            appendLine("═══ NETWORK SECURITY ═══")
            appendLine()
            appendLine("Connection: ${networkAudit.connectionType}")
            appendLine("VPN: ${if (networkAudit.isVpnActive) "✓ Active" else "Not active"}")
            if (networkAudit.wifiSSID != null) {
                appendLine("WiFi: ${networkAudit.wifiSSID} (${networkAudit.wifiSecurityType})")
                appendLine("WiFi Secure: ${if (networkAudit.isWifiSecure == true) "✓ Yes" else "⚠️ No"}")
            }
            appendLine()
            
            // Integrity Summary
            appendLine("═══ APK INTEGRITY ═══")
            appendLine()
            appendLine("Installer: ${integrity.installerType.name.replace('_', ' ')}")
            appendLine("Debug Mode: ${if (integrity.isDebuggable) "⚠️ YES" else "✓ No"}")
            appendLine("Signature: ${if (integrity.signatureMatchExpected) "✓ Valid" else "⚠️ Mismatch"}")
            appendLine("Tampering: ${if (integrity.tamperingDetected) "⚠️ DETECTED" else "✓ None"}")
            appendLine()
            
            // Permissions Summary
            appendLine("═══ PERMISSIONS ═══")
            appendLine()
            val dangerousGranted = permissions.filter { it.dangerous && it.granted }
            appendLine("Total: ${permissions.size}")
            appendLine("Granted: ${permissions.count { it.granted }}")
            appendLine("Dangerous Granted: ${dangerousGranted.size}")
            if (dangerousGranted.isNotEmpty()) {
                appendLine()
                appendLine("Dangerous permissions:")
                dangerousGranted.take(5).forEach {
                    appendLine("  • ${it.description}")
                }
                if (dangerousGranted.size > 5) {
                    appendLine("  ... and ${dangerousGranted.size - 5} more")
                }
            }
            appendLine()
            
            // Recommendations
            appendLine("═══ RECOMMENDATIONS ═══")
            appendLine()
            val recommendations = getRecommendations(systemAudit, networkAudit, permissions, integrity)
            recommendations.forEach { recommendation ->
                appendLine("• $recommendation")
            }
            appendLine()
            
            appendLine("═══ TRANSPARENCY ═══")
            appendLine()
            appendLine("✓ 100% local analysis - no cloud dependencies")
            appendLine("✓ No data transmitted - GDPR compliant")
            appendLine("✓ ANSSI audit-ready - reproducible results")
            appendLine()
            appendLine("Report ID: ${generateReportId()}")
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun generateReportId(): String {
        return "SR-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
}
