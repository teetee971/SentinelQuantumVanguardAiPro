package com.sentinel.quantum.ai

/** Installed only by trusted application composition. Never supplied by model output.
 * Callbacks must be bounded and synchronous: no deferred Android side effects.
 * validate is a strict per-action parser; permitted rechecks current Android roles/permissions.
 * No action is registered by default. No sensitive Phone Core operation is enabled here.
 */
data class SentinelAiActionPolicy(
    val validate: (String) -> Boolean,
    val permitted: () -> Boolean,
    val execute: (String) -> Unit
)

internal object SentinelAiInputBounds {
    private val actionId = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
    fun valid(action: String, parameters: String): Boolean =
        actionId.matches(action) && parameters.length <= 4096 && '\u0000' !in parameters
}
