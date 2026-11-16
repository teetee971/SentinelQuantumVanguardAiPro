package com.sentinel.quantum.security

/**
 * Pure fail-closed selection rule for outgoing-call lines.
 * One active line may be selected automatically; multiple active lines always require
 * an explicit user choice unless the previous choice is still active.
 */
object CallLineSelectionPolicy {
    data class State(
        val selectedId: String?,
        val explicitChoiceRequired: Boolean,
        val hasUsableLine: Boolean
    )

    fun reconcile(activeIds: List<String>, selectedId: String?): State {
        val active = activeIds.filter { it.isNotBlank() }.distinct()
        return when {
            active.isEmpty() -> State(null, explicitChoiceRequired = false, hasUsableLine = false)
            active.size == 1 -> State(active.first(), explicitChoiceRequired = false, hasUsableLine = true)
            selectedId != null && selectedId in active ->
                State(selectedId, explicitChoiceRequired = false, hasUsableLine = true)
            else -> State(null, explicitChoiceRequired = true, hasUsableLine = true)
        }
    }
}
