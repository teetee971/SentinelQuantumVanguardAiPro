package com.sentinel.quantum.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelApp
import com.sentinel.quantum.security.*
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * SecurityAuditService - Service d'audit passif en arrière-plan
 * 
 * Fonctionnalités:
 * - Audit sécurité périodique (toutes les 6 heures)
 * - Détection changements environnement
 * - Notifications alertes critiques
 * - Export automatique rapports
 * 
 * Mode:
 * - 100% passif (pas d'interception, pas d'attaque)
 * - Foreground service (transparent pour l'utilisateur)
 * - Économie batterie (intervalle raisonnable)
 * 
 * Conformité:
 * - GDPR (données locales uniquement)
 * - Android best practices
 * - Batterie optimisée
 */
class SecurityAuditService : Service() {

    companion object {
        const val TAG = "SecurityAuditService"
        const val NOTIFICATION_ID = 1001
        const val AUDIT_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 heures
        
        fun start(context: Context) {
            val intent = Intent(context, SecurityAuditService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, SecurityAuditService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var auditJob: Job? = null
    
    private lateinit var systemAudit: SystemAuditModule
    private lateinit var networkAudit: NetworkSecurityModule
    private lateinit var permissionScanner: PermissionScanner
    private lateinit var integrityVerifier: IntegrityVerifier
    private lateinit var riskDetector: RiskEnvironmentDetector
    private lateinit var reportBuilder: SecurityReportBuilder
    private lateinit var reportExporter: SecurityReportExporter

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialiser modules de sécurité
        systemAudit = SystemAuditModule(this)
        networkAudit = NetworkSecurityModule(this)
        permissionScanner = PermissionScanner(this)
        integrityVerifier = IntegrityVerifier(this)
        riskDetector = RiskEnvironmentDetector()
        reportBuilder = SecurityReportBuilder()
        reportExporter = SecurityReportExporter(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        
        // Démarrer en foreground
        startForeground(NOTIFICATION_ID, createNotification("Service started", "Running security audit..."))
        
        // Lancer audit périodique
        startPeriodicAudit()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Service non bindable
    }

    private fun startPeriodicAudit() {
        auditJob?.cancel()
        
        auditJob = serviceScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Running security audit...")
                    val auditResult = runSecurityAudit()
                    
                    // Vérifier alertes critiques
                    if (auditResult.isCritical) {
                        sendCriticalAlert(auditResult)
                    }
                    
                    // Mettre à jour notification
                    updateNotification("Audit complete", "Score: ${auditResult.score}/100")
                    
                    Log.d(TAG, "Audit complete. Score: ${auditResult.score}/100")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Audit failed", e)
                }
                
                // Attendre avant prochain audit
                delay(AUDIT_INTERVAL_MS)
            }
        }
    }

    private suspend fun runSecurityAudit(): AuditResult {
        return withContext(Dispatchers.IO) {
            val systemResult = systemAudit.runAudit()
            val networkResult = networkAudit.runAudit()
            val permissions = permissionScanner.scan()
            val integrityResult = integrityVerifier.verify()
            val riskResult = riskDetector.analyze()
            
            val jsonReport = reportBuilder.buildJson(
                systemResult,
                networkResult,
                permissions,
                integrityResult
            )
            
            val scoreObj = jsonReport.getJSONObject("score")
            val scoreValue = scoreObj.getInt("value")
            val scoreLevel = scoreObj.getString("level")
            
            // Exporter rapport si score critique/faible
            if (scoreValue < 65) {
                reportExporter.exportJson(jsonReport.toString(2))
            }
            
            AuditResult(
                score = scoreValue,
                level = scoreLevel,
                isCritical = scoreLevel == "CRITICAL" || scoreLevel == "WEAK",
                hasRootIndicators = riskResult.rootIndicators.isNotEmpty(),
                hasTampering = integrityResult.tamperingDetected
            )
        }
    }

    private fun sendCriticalAlert(result: AuditResult) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val title = "Security Alert"
        val message = when {
            result.hasTampering -> "APK tampering detected!"
            result.hasRootIndicators -> "Root/risky environment detected!"
            else -> "Security score low: ${result.score}/100"
        }
        
        val notification = NotificationCompat.Builder(this, SentinelApp.CHANNEL_SECURITY_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        
        notificationManager.notify(2000, notification)
    }

    private fun createNotification(title: String, message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, SentinelApp.CHANNEL_AUDIT_STATUS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, message))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        auditJob?.cancel()
        serviceScope.cancel()
    }

    data class AuditResult(
        val score: Int,
        val level: String,
        val isCritical: Boolean,
        val hasRootIndicators: Boolean,
        val hasTampering: Boolean
    )
}
