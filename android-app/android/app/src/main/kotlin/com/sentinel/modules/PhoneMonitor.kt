package com.sentinel.modules

/**
 * PhoneMonitor - Module de surveillance téléphonique LÉGAL
 * 
 * LÉGALITÉ:
 * - Aucune interception d'appels
 * - Aucune écoute
 * - Utilisation uniquement de métadonnées publiques
 * - Bases de données légales (ARCEP, listes publiques)
 * - Heuristiques locales
 * 
 * CONFORME: RGPD, CNIL, Réglementation télécoms française
 */
class PhoneMonitor {
    
    /**
     * Analyse un numéro entrant pour détecter les risques
     * 
     * @param number Numéro de téléphone à analyser
     * @return Évaluation du risque associé au numéro
     */
    fun onIncomingCall(number: String): PhoneRisk {
        // Vérifier dans la base locale de spam
        val isKnownSpam = SpamDatabase.contains(number)
        
        // Analyse heuristique locale
        val isShortCode = number.length < 5
        val isSuspiciousPattern = checkSuspiciousPattern(number)
        
        // Déterminer le niveau de risque
        val riskLevel = when {
            isKnownSpam -> "HIGH"
            isSuspiciousPattern -> "MEDIUM"
            isShortCode -> "LOW"
            else -> "SAFE"
        }
        
        // Générer une recommandation
        val recommendation = when (riskLevel) {
            "HIGH" -> "Bloquer recommandé - Spam confirmé"
            "MEDIUM" -> "Prudence - Pattern suspect détecté"
            "LOW" -> "Vérifier - Code court détecté"
            else -> "Autoriser - Aucun risque détecté"
        }
        
        // Logger l'événement
        LocalLogger.log(
            SecurityEvent(
                timestamp = System.currentTimeMillis(),
                type = "INCOMING_CALL",
                severity = if (isKnownSpam) "WARNING" else "INFO",
                explanation = "Appel entrant de $number - Risque: $riskLevel",
                source = "PhoneMonitor"
            )
        )
        
        return PhoneRisk(
            number = number,
            spam = isKnownSpam,
            riskLevel = riskLevel,
            recommendation = recommendation,
            source = "Local heuristics + spam database"
        )
    }
    
    /**
     * Vérifie si le numéro correspond à un pattern suspect
     * Heuristique locale basée sur les patterns connus
     */
    private fun checkSuspiciousPattern(number: String): Boolean {
        // Patterns suspects courants (sans être exhaustif)
        val suspiciousPatterns = listOf(
            "^0[89]", // Numéros surtaxés
            "^\\+9[0-9]{8,}$", // Préfixes internationaux suspects
            "^(.)\\1{5,}$" // Répétition excessive d'un chiffre
        )
        
        return suspiciousPatterns.any { pattern ->
            Regex(pattern).containsMatchIn(number)
        }
    }
    
    /**
     * Signale un numéro comme spam (ajout à la base locale)
     * 
     * @param number Numéro à signaler
     * @param reason Raison du signalement
     */
    fun reportSpam(number: String, reason: String) {
        SpamDatabase.add(number)
        
        LocalLogger.log(
            SecurityEvent(
                timestamp = System.currentTimeMillis(),
                type = "SPAM_REPORT",
                severity = "INFO",
                explanation = "Numéro $number signalé comme spam: $reason",
                source = "PhoneMonitor"
            )
        )
    }
}

/**
 * Évaluation du risque d'un numéro de téléphone
 * 
 * @property number Numéro analysé
 * @property spam Indique si le numéro est dans la base spam
 * @property riskLevel Niveau de risque (SAFE, LOW, MEDIUM, HIGH)
 * @property recommendation Recommandation d'action
 * @property source Source de l'information
 */
data class PhoneRisk(
    val number: String,
    val spam: Boolean,
    val riskLevel: String,
    val recommendation: String,
    val source: String
)

/**
 * Base de données locale de spam
 * 
 * En production, cette base serait alimentée par:
 * - ARCEP (Autorité de régulation)
 * - Listes publiques de spam
 * - Signalements utilisateurs
 * - Heuristiques locales
 * 
 * IMPORTANT: Aucune base de données externe n'est requise
 * Tout est stocké localement sur l'appareil
 */
object SpamDatabase {
    private val spamNumbers = mutableSetOf<String>()
    
    init {
        // Pré-remplir avec quelques numéros de test
        // En production, charger depuis une base locale
        loadDefaultSpamPatterns()
    }
    
    /**
     * Vérifie si un numéro est dans la base spam
     */
    fun contains(number: String): Boolean {
        return spamNumbers.contains(number)
    }
    
    /**
     * Ajoute un numéro à la base spam
     */
    fun add(number: String) {
        spamNumbers.add(number)
    }
    
    /**
     * Retire un numéro de la base spam
     */
    fun remove(number: String) {
        spamNumbers.remove(number)
    }
    
    /**
     * Charge les patterns de spam par défaut
     */
    private fun loadDefaultSpamPatterns() {
        // Exemples de numéros/patterns connus
        // En production, charger depuis un fichier local ou une base SQLite
        spamNumbers.addAll(listOf(
            // Exemples fictifs - à remplacer par vraies données
        ))
    }
    
    /**
     * Retourne le nombre de numéros en base
     */
    fun size(): Int = spamNumbers.size
}
