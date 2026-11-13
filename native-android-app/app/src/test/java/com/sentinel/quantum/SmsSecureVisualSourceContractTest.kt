package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSecureVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/SmsComposeActivity.kt"
    ).readText()

    @Test
    fun secureConversationSurfaceKeepsLocalAnalysisAndMmsTruth() {
        listOf(
            "secure-conversation-header",
            "Messages sécurisés",
            "Analyse locale du message",
            "message-risk-card",
            "Miniature MMS indisponible"
        ).forEach { assertTrue("Missing secure message marker: $it", source.contains(it)) }
        assertTrue(source.contains("smsAnalyzer.analyze(message.body)"))
        assertTrue(source.contains("contenu non destiné à l’enrichissement distant"))
    }
}
