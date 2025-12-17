package com.sentinel

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi

/**
 * Call Screening Service for Sentinel Phone Module
 * 
 * This service analyzes incoming calls BEFORE they ring and provides
 * risk assessment to the user.
 * 
 * IMPORTANT:
 * - NO recording or interception of call content
 * - Only analyzes call metadata (number, time)
 * - User consent required
 * - Fully transparent operation
 */
@RequiresApi(Build.VERSION_CODES.N)
class SentinelCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Extract call information
        val phoneNumber = callDetails.handle?.schemeSpecificPart
        
        if (phoneNumber.isNullOrEmpty()) {
            // Allow call if no number (privacy protected caller)
            respondToCall(callDetails, createAllowResponse())
            return
        }
        
        // Analyze the call (local processing only)
        val riskLevel = analyzeCallRisk(phoneNumber)
        
        // Send notification to React Native layer
        sendCallNotification(phoneNumber, riskLevel)
        
        // Always allow the call to ring - user decides
        // We don't block automatically (user control)
        respondToCall(callDetails, createAllowResponse())
    }
    
    /**
     * Analyze call risk based on phone number patterns
     * This runs locally on device - no external API calls
     */
    private fun analyzeCallRisk(phoneNumber: String): RiskLevel {
        // Basic pattern analysis
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // Check for suspicious patterns
        val hasSuspiciousPattern = when {
            // Repeated digits (e.g., 000000, 111111)
            Regex("(\\d)\\1{5,}").containsMatchIn(cleanNumber) -> true
            // Sequential numbers (e.g., 123456, 987654)
            cleanNumber.contains("123456") || cleanNumber.contains("987654") -> true
            else -> false
        }
        
        // Check country code (basic international detection)
        val isInternational = cleanNumber.startsWith("+") && !cleanNumber.startsWith("+33")
        
        // Check ARCEP ranges (France anti-spam)
        val isArcepRange = checkArcepRange(cleanNumber)
        
        // Calculate risk level
        return when {
            isArcepRange -> RiskLevel.MEDIUM // Marketing call
            hasSuspiciousPattern -> RiskLevel.HIGH
            isInternational -> RiskLevel.LOW_MEDIUM
            else -> RiskLevel.LOW
        }
    }
    
    /**
     * Check if number is in ARCEP France marketing ranges
     */
    private fun checkArcepRange(number: String): Boolean {
        val arcepPrefixes = listOf(
            "0162", "0163", "0270", "0271", "0377", "0378",
            "0424", "0425", "0568", "0569", "0948", "0949"
        )
        return arcepPrefixes.any { number.startsWith(it) }
    }
    
    /**
     * Send notification to React Native
     * 
     * Note: This requires a native module bridge to be fully implemented.
     * For V1, logging is used. Full integration planned for Phase 2.
     * 
     * Implementation will use:
     * - DeviceEventManager to send events to React Native
     * - CallDetectionService.ts to receive and process events
     * - IncomingCallAlert.tsx to display UI
     */
    private fun sendCallNotification(phoneNumber: String, riskLevel: RiskLevel) {
        // V1: Log for debugging
        android.util.Log.d("SentinelCallScreening", 
            "Incoming call from $phoneNumber - Risk: $riskLevel")
        
        // TODO Phase 2: Implement React Native bridge
        // val eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        // eventEmitter.emit("onIncomingCallDetected", createCallEventData(phoneNumber, riskLevel))
    }
    
    /**
     * Create response that allows the call
     */
    private fun createAllowResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
    }
    
    enum class RiskLevel {
        LOW,        // Safe
        LOW_MEDIUM, // Slightly suspicious
        MEDIUM,     // Marketing/spam likely
        HIGH,       // Scam likely
        CRITICAL    // Very high risk
    }
}
