package com.sentinel.quantum.ai

enum class AiExecutionLocation {
    LOCAL, REMOTE
}

data class AiEngineCandidate(
    val engineId: String,
    val modelId: String,
    val location: AiExecutionLocation,
    val available: Boolean,
    val supportsText: Boolean
)

object SentinelAiEngineRouter {

    fun select(
        candidates: List<AiEngineCandidate>,
        remoteAllowed: Boolean
    ): AiEngineCandidate? {
        return candidates
            .asSequence()
            .filter {
                it.engineId.isNotBlank() &&
                    it.modelId.isNotBlank() &&
                    it.available &&
                    it.supportsText
            }
            .filter {
                it.location == AiExecutionLocation.LOCAL ||
                    remoteAllowed
            }
            .sortedWith(
                compareBy<AiEngineCandidate>(
                    { if (it.location == AiExecutionLocation.LOCAL) 0 else 1 },
                    { it.engineId },
                    { it.modelId }
                )
            )
            .firstOrNull()
    }
}
