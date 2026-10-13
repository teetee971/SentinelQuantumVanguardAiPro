package com.sentinel.quantum.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallScreeningIntelligenceBoundaryTest {
    @Test
    fun `intelligence publication occurs only inside post-response work`() {
        val source = callScreeningSource()
        val responseIndex = source.indexOf("respondToCall(callDetails, response.build())")
        val workerIndex = source.indexOf("POST_RESPONSE_WORKER.execute", startIndex = responseIndex.coerceAtLeast(0))
        val publicationIndex = source.indexOf("IntelligenceCoreRuntime.publishCallDecision(", startIndex = workerIndex.coerceAtLeast(0))

        assertTrue("screening must render the Android response", responseIndex >= 0)
        assertTrue("post-response worker must remain after respondToCall", workerIndex > responseIndex)
        assertTrue(
            "Intelligence call evidence must be published only from post-response work",
            publicationIndex > workerIndex
        )
    }

    @Test
    fun `critical path never reads intelligence persistence contacts remote reputation or aggregator`() {
        val source = callScreeningSource()
        val responseIndex = source.indexOf("respondToCall(callDetails, response.build())")
        assertTrue("screening must render the Android response", responseIndex >= 0)

        val preResponse = source.substring(0, responseIndex)
        listOf(
            "IntelligenceSnapshotStore",
            "IntelligenceAggregator",
            "LocalContactLookup",
            "CallerReputationClient",
            "BoundedIntelligenceEventSink"
        ).forEach { forbidden ->
            assertTrue(
                "$forbidden must not be used before respondToCall",
                !preResponse.contains(forbidden)
            )
        }
    }

    private fun callScreeningSource(): String {
        val relative = "app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        val candidates = listOf(
            File(relative),
            File("native-android-app/$relative")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("SentinelCallScreeningService.kt not found from ${File(".").absolutePath}")
        return source.readText()
    }
}
