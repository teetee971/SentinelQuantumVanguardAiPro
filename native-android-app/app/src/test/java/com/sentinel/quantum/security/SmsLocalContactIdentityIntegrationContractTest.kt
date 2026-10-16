package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLocalContactIdentityIntegrationContractTest {
    @Test
    fun conversationThreadsResolveLocalContactsOffMainBeforeRendering() {
        val sourceRoot = locateSourceRoot()
        val compose = File(
            sourceRoot,
            "com/sentinel/quantum/SmsComposeActivity.kt"
        ).readText()

        assertTrue(
            "SMS conversations must reuse the local Android contact resolver",
            compose.contains("LocalContactLookup(applicationContext)")
        )
        assertTrue(
            "Contact resolution for SMS conversations must stay off the Compose/main thread",
            compose.contains("SmsConversationIdentityPolicy.resolve(") &&
                compose.contains("withContext(Dispatchers.IO)")
        )
        assertTrue(
            "Conversation rendering must consume provenance-aware resolved identity",
            compose.contains("conversationIdentities[thread.address]") &&
                compose.contains("resolvedIdentity.displayLabel") &&
                compose.contains("resolvedIdentity.provenance")
        )
    }

    private fun locateSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("native-android-app/app/src/main/java")
        )
        return candidates.firstOrNull { root ->
            File(root, "com/sentinel/quantum/SmsComposeActivity.kt").isFile
        } ?: error("Unable to locate Android main source root from ${File(".").absolutePath}")
    }
}
