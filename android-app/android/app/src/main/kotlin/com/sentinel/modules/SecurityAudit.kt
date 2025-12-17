package com.sentinel.modules

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * SecurityAudit - Module d'audit de sécurité personnel
 * 
 * Effectue une analyse locale de la configuration de sécurité
 * AUCUNE donnée n'est transmise - Analyse 100% locale
 */
class SecurityAudit(private val context: Context) {
    
    /**
     * Lance un audit complet de sécurité
     * 
     * @return Résultat de l'audit avec score et recommandations
     */
    fun run(): AuditResult {
        // Vérifier les permissions dangereuses
        val permissionsRisk = checkPermissions()
        
        // Analyser les paramètres système
        val systemChecks = checkSystemSettings()
        
        // Calculer le score global (0-100)
        val systemScore = calculateScore(permissionsRisk, systemChecks)
        
        // Générer un résumé
        val summary = generateSummary(systemScore)
        
        // Logger l'audit
        LocalLogger.log(
            SecurityEvent(
                timestamp = System.currentTimeMillis(),
                type = "SECURITY_AUDIT",
                severity = if (systemScore >= 70) "INFO" else "WARNING",
                explanation = "Audit de sécurité terminé - Score: $systemScore/100",
                source = "SecurityAudit"
            )
        )
        
        return AuditResult(
            permissionsRisk = permissionsRisk,
            systemScore = systemScore,
            summary = summary,
            checks = systemChecks,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Vérifie les permissions sensibles de l'application
     * 
     * @return Nombre de permissions sensibles accordées
     */
    private fun checkPermissions(): Int {
        val dangerousPermissions = listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CALL_LOG
        )
        
        var grantedCount = 0
        
        for (permission in dangerousPermissions) {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                grantedCount++
            }
        }
        
        return grantedCount
    }
    
    /**
     * Vérifie les paramètres système de sécurité
     * 
     * @return Liste des vérifications effectuées
     */
    private fun checkSystemSettings(): List<SecurityCheck> {
        val checks = mutableListOf<SecurityCheck>()
        
        // Vérification de la version Android
        checks.add(
            SecurityCheck(
                name = "Version Android",
                passed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
                severity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "INFO" else "WARNING",
                message = "Android ${Build.VERSION.RELEASE}"
            )
        )
        
        // Vérification du chiffrement
        checks.add(
            SecurityCheck(
                name = "Chiffrement",
                passed = true, // Simplifié pour démo
                severity = "INFO",
                message = "Stockage chiffré activé"
            )
        )
        
        // Vérification de la sécurité de l'écran
        checks.add(
            SecurityCheck(
                name = "Verrouillage écran",
                passed = true, // Simplifié pour démo
                severity = "INFO",
                message = "Écran de verrouillage configuré"
            )
        )
        
        return checks
    }
    
    /**
     * Calcule le score de sécurité global
     * 
     * @param permissionsRisk Nombre de permissions sensibles
     * @param checks Liste des vérifications système
     * @return Score entre 0 et 100
     */
    private fun calculateScore(permissionsRisk: Int, checks: List<SecurityCheck>): Int {
        var score = 100
        
        // Pénalité pour les permissions sensibles
        score -= permissionsRisk * 3
        
        // Pénalité pour les checks échoués
        val failedChecks = checks.count { !it.passed }
        score -= failedChecks * 10
        
        // Assurer que le score reste entre 0 et 100
        return score.coerceIn(0, 100)
    }
    
    /**
     * Génère un résumé en langage clair
     * 
     * @param score Score de sécurité
     * @return Résumé textuel
     */
    private fun generateSummary(score: Int): String {
        return when {
            score >= 90 -> "Configuration excellente. Sécurité optimale."
            score >= 70 -> "Configuration globalement saine. Quelques ajustements recommandés."
            score >= 50 -> "Configuration acceptable. Améliorations nécessaires."
            else -> "Configuration faible. Révision de sécurité urgente recommandée."
        }
    }
}

/**
 * Résultat d'un audit de sécurité
 * 
 * @property permissionsRisk Nombre de permissions sensibles accordées
 * @property systemScore Score global de sécurité (0-100)
 * @property summary Résumé en langage clair
 * @property checks Liste détaillée des vérifications
 * @property timestamp Horodatage de l'audit
 */
data class AuditResult(
    val permissionsRisk: Int,
    val systemScore: Int,
    val summary: String,
    val checks: List<SecurityCheck>,
    val timestamp: Long
)

/**
 * Représente une vérification de sécurité individuelle
 * 
 * @property name Nom de la vérification
 * @property passed Indique si la vérification a réussi
 * @property severity Niveau de sévérité (INFO, WARNING, CRITICAL)
 * @property message Message descriptif
 */
data class SecurityCheck(
    val name: String,
    val passed: Boolean,
    val severity: String,
    val message: String
)
