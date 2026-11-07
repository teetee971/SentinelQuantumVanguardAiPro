package com.sentinel.quantum.security

/** Plain text, no private absolute paths. User chooses whether to share it. */
object SentinelCleanupReport {
    fun render(results: List<SentinelCleanupPolicy.Result>): String = buildString {
        val summary = SentinelCleanupBatch.summarize(results)
        appendLine("Rapport de nettoyage Sentinel")
        appendLine("Copies temporaires Sentinel uniquement")
        appendLine("Demandés : ${summary.requestedCount}")
        appendLine("Suppressions vérifiées : ${results.count { it.isVerifiedCleaned && it.executionEffect == SentinelCleanupPolicy.ExecutionEffect.REMOVED_BY_EXECUTION }}")
        appendLine("Déjà absents : ${results.count { it.isVerifiedCleaned && it.executionEffect == SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT }}")
        appendLine("Échecs : ${summary.failedCount}")
        appendLine("Non résolus ou protégés : ${summary.unresolvedCount}")
        appendLine("Volume logique supprimé : ${summary.knownVerifiedFreedBytes}")
        appendLine("Tailles des copies supprimées ; espace disque réellement récupéré non mesuré.")
        appendLine("Tailles libérées inconnues : ${summary.unknownFreedSizeCount}")
        results.take(100).forEach { result ->
            val name = result.before.displayName.take(120).map {
                if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it
            }.joinToString("")
            val label = when (result.after.state) {
                SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED -> if (result.executionEffect == SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT) "déjà absent" else "suppression vérifiée"
                SentinelCleanupPolicy.ActionState.FAILED -> "échec"
                else -> "non supprimé ou non vérifié"
            }
            appendLine("$name : $label")
        }
        if (results.size > 100) appendLine("${results.size - 100} autres résultats inclus dans les totaux.")
    }
}
