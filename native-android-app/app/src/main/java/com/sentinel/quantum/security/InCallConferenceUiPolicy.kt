package com.sentinel.quantum.security

/**
 * Pure presentation policy for conference targets exposed by Android Telecom.
 *
 * Target identifiers are opaque process-local tokens. Phone numbers are intentionally not
 * required for presentation; a bounded display name is used when Android supplies one.
 */
object InCallConferenceUiPolicy {
    data class Target(
        val id: String,
        val displayName: String? = null
    )

    data class PresentedTarget(
        val id: String,
        val label: String
    )

    fun present(targets: List<Target>): List<PresentedTarget> {
        val seen = HashSet<String>()
        return targets.asSequence()
            .filter { it.id.isNotBlank() && seen.add(it.id) }
            .take(MAX_TARGETS)
            .mapIndexed { index, target ->
                val safeName = target.displayName
                    ?.trim()
                    ?.take(MAX_LABEL_CHARS)
                    ?.takeIf { it.isNotBlank() }
                PresentedTarget(
                    id = target.id,
                    label = safeName ?: "Autre appel ${index + 1}"
                )
            }
            .toList()
    }

    private const val MAX_TARGETS = 4
    private const val MAX_LABEL_CHARS = 80
}
