package com.sentinel.quantum.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallScreeningIntelligenceBoundaryTest {
    @Test
    fun `intelligence publication occurs only inside post-response work`() {
        val source = callScreeningSource()
        val responseIndex = source.indexOf("respondToCall(callDetails, response.build())")
        val postResponseIndex = source.indexOf("responseBoundary.submitPostResponse")
        val publicationIndex = source.indexOf(
            "IntelligenceCoreRuntime.publishCallDecision(",
            startIndex = postResponseIndex.coerceAtLeast(0)
        )

        assertTrue("screening must render the Android response", responseIndex >= 0)
        assertTrue(
            "post-response work must be submitted only after the normal Telecom response",
            postResponseIndex > responseIndex
        )
        assertTrue(
            "Intelligence call evidence must be published only from boundary-guarded post-response work",
            publicationIndex > postResponseIndex
        )
    }

    @Test
    fun `critical path never reads intelligence persistence contacts remote reputation or aggregator`() {
        val source = callScreeningSource()
        val postResponseIndex = source.indexOf("responseBoundary.submitPostResponse")
        assertTrue("screening must expose a guarded post-response boundary", postResponseIndex >= 0)

        val preResponseWork = source.substring(0, postResponseIndex)
        listOf(
            "IntelligenceSnapshotStore",
            "IntelligenceAggregator",
            "LocalContactLookup",
            "CallerReputationClient",
            "BoundedIntelligenceEventSink",
            "IntelligenceCoreRuntime.publishCallDecision("
        ).forEach { forbidden ->
            assertTrue(
                "$forbidden must not be used before boundary-guarded post-response work",
                !preResponseWork.contains(forbidden)
            )
        }
    }

    private fun callScreeningSource(): String {
        val packagePath = "com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        val candidates = listOf(
            File("src/main/java/$packagePath"),
            File("app/src/main/java/$packagePath"),
            File("native-android-app/app/src/main/java/$packagePath")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error(
                "SentinelCallScreeningService.kt not found from ${File(".").absolutePath}; " +
                    "checked ${candidates.joinToString { it.path }}"
            )
        return source.readText()
    }
}
