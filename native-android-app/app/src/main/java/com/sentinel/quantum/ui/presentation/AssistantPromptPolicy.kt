package com.sentinel.quantum.ui.presentation

/** Deterministic local guidance until a separately qualified AI provider is connected. */
object AssistantPromptPolicy {
    fun respond(prompt: String): String {
        val normalized = prompt.trim().lowercase()
        val guidance = when {
            normalized.isBlank() ->
                "Posez une question sur un risque, une alerte ou un numéro pour recevoir un conseil contextualisé."
            "numéro" in normalized || "numero" in normalized || "appel" in normalized ->
                "Vérifiez le numéro avec sa provenance, l’état local et une source indépendante avant toute action sensible."
            "alerte" in normalized || "phishing" in normalized || "fraude" in normalized ->
                "Ouvrez les éléments de provenance et évitez les liens ou demandes urgentes tant que la source n’est pas vérifiée."
            else ->
                "Conservez les permissions minimales, vérifiez les paramètres Android et documentez la source du signal."
        }
        return "$guidance Ceci est un conseil Sentinel local, pas une preuve technique ni une certification."
    }
}
