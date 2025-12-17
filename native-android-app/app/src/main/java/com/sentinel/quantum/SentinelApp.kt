package com.sentinel.quantum

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * SentinelApp - Application principale
 * 
 * Initialisation globale:
 * - Canaux de notification
 * - Configuration sécurité
 * - Logging
 * 
 * Privacy & Security:
 * - Pas de tracking
 * - Pas d'analytics
 * - Données 100% locales
 */
class SentinelApp : Application() {

    companion object {
        const val TAG = "SentinelApp"
        const val CHANNEL_SECURITY_ALERTS = "security_alerts"
        const val CHANNEL_AUDIT_STATUS = "audit_status"
        
        lateinit var instance: SentinelApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "Sentinel Quantum Vanguard AI Pro initializing...")
        
        // Créer canaux de notification
        createNotificationChannels()
        
        // Configuration sécurité
        initializeSecurity()
        
        Log.d(TAG, "Application initialized successfully")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal pour alertes sécurité critiques
            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security alerts and warnings"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Canal pour statut audit
            val auditChannel = NotificationChannel(
                CHANNEL_AUDIT_STATUS,
                "Audit Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security audit service status"
                enableVibration(false)
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannel(securityChannel)
            notificationManager.createNotificationChannel(auditChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }

    private fun initializeSecurity() {
        // Configuration sécurité de base
        // Pas de tracking, pas d'analytics, données locales uniquement
        
        Log.d(TAG, "Security configuration:")
        Log.d(TAG, "  - Analytics: DISABLED")
        Log.d(TAG, "  - Tracking: DISABLED")
        Log.d(TAG, "  - Cloud sync: DISABLED")
        Log.d(TAG, "  - Data storage: LOCAL ONLY")
        Log.d(TAG, "  - Cleartext traffic: DISABLED")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory condition detected")
        // Cleanup si nécessaire
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory: level=$level")
        // Gérer selon le niveau
    }
}
