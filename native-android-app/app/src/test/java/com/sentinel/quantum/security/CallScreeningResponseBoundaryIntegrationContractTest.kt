package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScreeningResponseBoundaryIntegrationContractTest {
    @Test
    fun screening_service_routes_every_platform_response_through_per_call_boundary() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        ).readText()

        val boundaryIndex = source.indexOf("val responseBoundary = CallScreeningResponseBoundary")
        val roleReadIndex = source.indexOf("AndroidRoleReadPolicy.readBoolean")
        val postResponseIndex = source.indexOf("responseBoundary.submitPostResponse")

        assertTrue(boundaryIndex >= 0)
        assertTrue(roleReadIndex > boundaryIndex)
        assertTrue(postResponseIndex > roleReadIndex)
        assertEquals(4, Regex("responseBoundary\\.deliverResponse\\s*\\{").findAll(source).count())
        assertEquals(4, Regex("respondToCall\\(callDetails,").findAll(source).count())
        assertFalse(
            "no direct Telecom response may appear before the per-call boundary is created",
            source.substring(0, boundaryIndex).contains("respondToCall(callDetails,")
        )
    }

    @Test
    fun optional_work_uses_existing_bounded_worker_only_through_boundary() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        ).readText()

        val boundaryCreation = source.substringAfter("val responseBoundary = CallScreeningResponseBoundary")
            .substringBefore("// PII-free lifecycle marker")
        val postResponseBlock = source.substringAfter("val submitted = responseBoundary.submitPostResponse")
            .substringBefore("if (!submitted)")

        assertTrue(boundaryCreation.contains("POST_RESPONSE_WORKER.execute(task)"))
        assertFalse(postResponseBlock.contains("POST_RESPONSE_WORKER.execute"))
        assertTrue(postResponseBlock.contains("CallFilterLogStore.get(appContext).recordAsync(effectiveDecision)"))
        assertTrue(postResponseBlock.contains("PhonePrivateTimelineStore(appContext).append"))
    }
}
