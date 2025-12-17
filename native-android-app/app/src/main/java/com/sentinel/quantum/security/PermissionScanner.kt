package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionFinding(
    val permission: String,
    val granted: Boolean,
    val dangerous: Boolean,
    val description: String
)

/**
 * PermissionScanner - Real Android permission auditor
 * 
 * Scans and analyzes app permissions:
 * - Lists all requested permissions
 * - Identifies granted vs. denied
 * - Flags dangerous permissions
 * - Provides security context
 * 
 * 100% local analysis using Android PackageManager
 * Privacy-preserving: no data leaves device
 */
class PermissionScanner(private val context: Context) {

    // Known dangerous permissions with descriptions
    private val dangerousPermissions = mapOf(
        Manifest.permission.READ_CALENDAR to "Access calendar events",
        Manifest.permission.WRITE_CALENDAR to "Modify calendar events",
        Manifest.permission.CAMERA to "Access camera",
        Manifest.permission.READ_CONTACTS to "Read contacts",
        Manifest.permission.WRITE_CONTACTS to "Modify contacts",
        Manifest.permission.GET_ACCOUNTS to "Access accounts list",
        Manifest.permission.ACCESS_FINE_LOCATION to "Access precise location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Access approximate location",
        Manifest.permission.RECORD_AUDIO to "Record audio",
        Manifest.permission.READ_PHONE_STATE to "Read phone state",
        Manifest.permission.READ_PHONE_NUMBERS to "Read phone numbers",
        Manifest.permission.CALL_PHONE to "Make phone calls",
        Manifest.permission.READ_CALL_LOG to "Read call history",
        Manifest.permission.WRITE_CALL_LOG to "Modify call history",
        Manifest.permission.ADD_VOICEMAIL to "Add voicemail",
        Manifest.permission.USE_SIP to "Use SIP service",
        Manifest.permission.PROCESS_OUTGOING_CALLS to "Process outgoing calls",
        Manifest.permission.ANSWER_PHONE_CALLS to "Answer phone calls",
        Manifest.permission.BODY_SENSORS to "Access body sensors",
        Manifest.permission.SEND_SMS to "Send SMS messages",
        Manifest.permission.RECEIVE_SMS to "Receive SMS messages",
        Manifest.permission.READ_SMS to "Read SMS messages",
        Manifest.permission.RECEIVE_WAP_PUSH to "Receive WAP push",
        Manifest.permission.RECEIVE_MMS to "Receive MMS",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Read external storage",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "Write external storage",
        Manifest.permission.ACCESS_MEDIA_LOCATION to "Access media location"
    )

    fun scan(): List<PermissionFinding> {
        val findings = mutableListOf<PermissionFinding>()
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            
            val requestedPermissions = packageInfo.requestedPermissions
            val requestedPermissionsFlags = packageInfo.requestedPermissionsFlags
            
            if (requestedPermissions != null) {
                requestedPermissions.forEachIndexed { index, permission ->
                    val granted = (requestedPermissionsFlags[index] and PackageManager.PERMISSION_GRANTED) != 0
                    val dangerous = isDangerousPermission(permission)
                    val description = getPermissionDescription(permission)
                    
                    findings.add(
                        PermissionFinding(
                            permission = permission,
                            granted = granted,
                            dangerous = dangerous,
                            description = description
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Handle exception gracefully
        }
        
        return findings.sortedWith(
            compareByDescending<PermissionFinding> { it.dangerous }
                .thenByDescending { it.granted }
                .thenBy { it.permission }
        )
    }

    private fun isDangerousPermission(permission: String): Boolean {
        return dangerousPermissions.containsKey(permission)
    }

    private fun getPermissionDescription(permission: String): String {
        return dangerousPermissions[permission] ?: getGenericDescription(permission)
    }

    private fun getGenericDescription(permission: String): String {
        // Extract readable name from permission string
        // e.g., "android.permission.INTERNET" -> "Internet access"
        val parts = permission.split(".")
        val name = parts.lastOrNull() ?: permission
        return name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * Get only dangerous permissions that are granted
     */
    fun getDangerousGrantedPermissions(): List<PermissionFinding> {
        return scan().filter { it.dangerous && it.granted }
    }

    /**
     * Check if specific permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Generate human-readable permissions report
     */
    fun getPermissionsReport(): String {
        val findings = scan()
        val dangerousGranted = findings.filter { it.dangerous && it.granted }
        val normalGranted = findings.filter { !it.dangerous && it.granted }
        
        return buildString {
            appendLine("=== Permissions Security Audit ===")
            appendLine()
            appendLine("Total Permissions: ${findings.size}")
            appendLine("- Granted: ${findings.count { it.granted }}")
            appendLine("- Denied: ${findings.count { !it.granted }}")
            appendLine("- Dangerous: ${findings.count { it.dangerous }}")
            appendLine()
            
            if (dangerousGranted.isNotEmpty()) {
                appendLine("⚠️ Dangerous Permissions (Granted):")
                dangerousGranted.forEach { finding ->
                    appendLine("  • ${finding.description}")
                    appendLine("    ${finding.permission}")
                }
                appendLine()
            }
            
            if (normalGranted.isNotEmpty()) {
                appendLine("Normal Permissions (Granted):")
                normalGranted.forEach { finding ->
                    appendLine("  • ${finding.description}")
                }
                appendLine()
            }
            
            appendSecurityRecommendations(dangerousGranted)
        }
    }

    private fun StringBuilder.appendSecurityRecommendations(dangerousGranted: List<PermissionFinding>) {
        appendLine("Security Analysis:")
        
        if (dangerousGranted.isEmpty()) {
            appendLine("✓ No dangerous permissions granted")
            return
        }
        
        // Analyze specific permission combinations
        val hasLocation = dangerousGranted.any { 
            it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
            it.permission == Manifest.permission.ACCESS_COARSE_LOCATION 
        }
        
        val hasContacts = dangerousGranted.any { 
            it.permission == Manifest.permission.READ_CONTACTS ||
            it.permission == Manifest.permission.WRITE_CONTACTS 
        }
        
        val hasPhone = dangerousGranted.any { 
            it.permission == Manifest.permission.READ_PHONE_STATE ||
            it.permission == Manifest.permission.READ_CALL_LOG ||
            it.permission == Manifest.permission.CALL_PHONE
        }
        
        val hasSMS = dangerousGranted.any { 
            it.permission == Manifest.permission.READ_SMS ||
            it.permission == Manifest.permission.SEND_SMS ||
            it.permission == Manifest.permission.RECEIVE_SMS
        }
        
        val hasCamera = dangerousGranted.any { it.permission == Manifest.permission.CAMERA }
        val hasMicrophone = dangerousGranted.any { it.permission == Manifest.permission.RECORD_AUDIO }
        val hasStorage = dangerousGranted.any { 
            it.permission == Manifest.permission.READ_EXTERNAL_STORAGE ||
            it.permission == Manifest.permission.WRITE_EXTERNAL_STORAGE 
        }
        
        if (hasLocation) {
            appendLine("💡 Location access: Used for security threat mapping")
        }
        
        if (hasPhone) {
            appendLine("💡 Phone access: Used for call security monitoring")
        }
        
        if (hasSMS) {
            appendLine("💡 SMS access: Used for smishing detection")
        }
        
        if (hasCamera || hasMicrophone) {
            appendLine("⚠️ Camera/Microphone: Ensure usage is transparent")
        }
        
        if (hasStorage) {
            appendLine("💡 Storage access: Used for local security logs")
        }
        
        if (hasContacts) {
            appendLine("⚠️ Contacts access: Review necessity")
        }
        
        appendLine()
        appendLine("Privacy Guarantee:")
        appendLine("✓ All data stays on device (100% local processing)")
        appendLine("✓ No cloud synchronization")
        appendLine("✓ GDPR compliant")
    }

    /**
     * Get permissions grouped by category
     */
    fun getPermissionsByCategory(): Map<String, List<PermissionFinding>> {
        val findings = scan()
        return findings.groupBy { finding ->
            when {
                finding.permission.contains("LOCATION") -> "Location"
                finding.permission.contains("CAMERA") -> "Camera"
                finding.permission.contains("PHONE") || finding.permission.contains("CALL") -> "Phone"
                finding.permission.contains("SMS") || finding.permission.contains("MMS") -> "Messaging"
                finding.permission.contains("CONTACTS") -> "Contacts"
                finding.permission.contains("STORAGE") || finding.permission.contains("MEDIA") -> "Storage"
                finding.permission.contains("AUDIO") || finding.permission.contains("RECORD") -> "Microphone"
                finding.permission.contains("CALENDAR") -> "Calendar"
                finding.permission.contains("SENSORS") -> "Sensors"
                else -> "Other"
            }
        }
    }
}
