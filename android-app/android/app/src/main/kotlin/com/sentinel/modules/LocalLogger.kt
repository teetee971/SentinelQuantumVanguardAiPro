package com.sentinel.modules

/**
 * LocalLogger - Journal local des événements de sécurité
 * 
 * Stockage 100% local, aucune transmission réseau
 * Conforme RGPD - données sur l'appareil uniquement
 */
object LocalLogger {
    private val logs = mutableListOf<SecurityEvent>()
    
    /**
     * Enregistre un événement de sécurité
     * @param event Événement à enregistrer
     */
    fun log(event: SecurityEvent) {
        logs.add(event)
        
        // Limiter à 1000 événements max pour éviter surcharge mémoire
        if (logs.size > 1000) {
            logs.removeAt(0)
        }
    }
    
    /**
     * Récupère tous les événements
     * @return Liste complète des événements
     */
    fun getAll(): List<SecurityEvent> = logs.toList()
    
    /**
     * Récupère les événements par niveau de sévérité
     * @param severity Niveau de sévérité (INFO, WARNING, CRITICAL)
     * @return Liste filtrée des événements
     */
    fun getBySeverity(severity: String): List<SecurityEvent> {
        return logs.filter { it.severity == severity }
    }
    
    /**
     * Récupère les N derniers événements
     * @param count Nombre d'événements à récupérer
     * @return Liste des derniers événements
     */
    fun getRecent(count: Int): List<SecurityEvent> {
        return logs.takeLast(count)
    }
    
    /**
     * Efface tous les événements
     */
    fun clear() {
        logs.clear()
    }
    
    /**
     * Compte le nombre d'événements
     * @return Nombre total d'événements
     */
    fun count(): Int = logs.size
}

/**
 * Représente un événement de sécurité
 * 
 * @property timestamp Horodatage Unix en millisecondes
 * @property type Type d'événement (connexion, permission, scan, etc.)
 * @property severity Niveau de sévérité (INFO, WARNING, CRITICAL)
 * @property explanation Explication humaine de l'événement
 * @property source Source de l'événement (module qui l'a généré)
 */
data class SecurityEvent(
    val timestamp: Long,
    val type: String,
    val severity: String,
    val explanation: String,
    val source: String = "SYSTEM"
) {
    /**
     * Convertit l'événement en format lisible
     */
    override fun toString(): String {
        return "[$severity] $type - $explanation (source: $source)"
    }
}
