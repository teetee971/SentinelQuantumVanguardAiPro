package com.sentinel.quantum.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallScreeningIntelligenceBoundaryTest {
    @Test
    fun `intelligence publication occurs only inside post-response work`() {
        val source = callScreeningSource()
        val deliveryIndex = source.indexOf("responseBoundary.deliverResponse {")
        val responseIndex = source.indexOf(
            "respondToCall(callDetails, response.build())",
            startIndex = deliveryIndex.coerceAtLeast(0)
        )
        val postResponseSubmissionIndex = source.indexOf(
            "responseBoundary.submitPostResponse {",
            startIndex = responseIndex.coerceAtLeast(0)
        )
        val publicationIndex = source.indexOf(
            "IntelligenceCoreRuntime.publishCallDecision(",
            startIndex = postResponseSubmissionIndex.coerceAtLeast(0)
        )

        assertTrue("normal screening response delivery must exist", deliveryIndex >= 0)
        assertTrue("screening must render the Android response", responseIndex > deliveryIndex)
        assertTrue(
            "post-response submission must remain after respondToCall",
            postResponseSubmissionIndex > responseIndex
        )
        assertTrue(
            "Intelligence call evidence must be published only from post-response work",
            publicationIndex > postResponseSubmissionIndex
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
