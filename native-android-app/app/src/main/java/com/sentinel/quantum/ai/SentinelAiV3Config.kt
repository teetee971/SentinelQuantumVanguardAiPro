package com.sentinel.quantum.ai

enum class AiEngineMode { AUTO }
enum class AiModelMode { AUTO }
enum class AiVisualMode { QUANTUM_ORB }

data class SentinelAiV3Config(
    val engine: AiEngineMode = AiEngineMode.AUTO,
    val model: AiModelMode = AiModelMode.AUTO,
    val visual: AiVisualMode = AiVisualMode.QUANTUM_ORB,
    val voiceResponsesEnabled: Boolean = true,
    val sensitiveActionConfirmationEnabled: Boolean = true
) {
    init {
        require(sensitiveActionConfirmationEnabled) {
            "Sensitive action confirmation cannot be disabled"
        }
    }
}
