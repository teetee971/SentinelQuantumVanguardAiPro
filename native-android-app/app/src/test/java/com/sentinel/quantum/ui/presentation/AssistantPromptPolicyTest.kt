package com.sentinel.quantum.ui.presentation

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptPolicyTest {
    @Test
    fun everyLocalAnswerStatesThatItIsAdviceNotProof() {
        listOf("Quel est le risque ?", "Explique cette alerte", "Analyse ce numéro").forEach { prompt ->
            val answer = AssistantPromptPolicy.respond(prompt)
            assertTrue(answer.contains("conseil", ignoreCase = true))
            assertTrue(answer.contains("preuve", ignoreCase = true))
        }
    }

    @Test
    fun blankQuestionsRemainActionableWithoutInventingEvidence() {
        val answer = AssistantPromptPolicy.respond(" ")
        assertTrue(answer.contains("question", ignoreCase = true))
        assertTrue(answer.contains("preuve", ignoreCase = true))
    }
}
