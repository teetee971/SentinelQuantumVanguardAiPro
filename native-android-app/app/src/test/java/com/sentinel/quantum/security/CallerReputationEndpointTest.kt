package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallerReputationEndpointTest {
    @Test fun buildsEvaluationPathFromHttpsBaseUrl() {
        assertEquals(
            "https://sentinel.example/v1/evaluate-call",
            CallerReputationClient.endpoint("https://sentinel.example/")
        )
    }

    @Test(expected = SecurityException::class)
    fun rejectsCleartextEndpoint() {
        CallerReputationClient.endpoint("http://sentinel.example")
    }

    @Test(expected = SecurityException::class)
    fun rejectsEndpointWithCredentials() {
        CallerReputationClient.endpoint("https://user:secret@sentinel.example")
    }
}
