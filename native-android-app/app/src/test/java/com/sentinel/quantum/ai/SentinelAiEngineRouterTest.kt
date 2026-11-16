package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiEngineRouterTest {

    private fun candidate(
        engine: String,
        location: AiExecutionLocation,
        available: Boolean = true,
        supportsText: Boolean = true
    ) = AiEngineCandidate(
        engineId = engine,
        modelId = "model-1",
        location = location,
        available = available,
        supportsText = supportsText
    )

    @Test
    fun noCandidatesMeansNoSelection() {
        assertNull(SentinelAiEngineRouter.select(emptyList(), false))
    }

    @Test
    fun localEngineHasPriority() {
        val remote = candidate("remote", AiExecutionLocation.REMOTE)
        val local = candidate("local", AiExecutionLocation.LOCAL)

        assertEquals(
            local,
            SentinelAiEngineRouter.select(listOf(remote, local), true)
        )
    }

    @Test
    fun remoteRequiresExplicitAuthorization() {
        val remote = candidate("remote", AiExecutionLocation.REMOTE)

        assertNull(SentinelAiEngineRouter.select(listOf(remote), false))
        assertEquals(
            remote,
            SentinelAiEngineRouter.select(listOf(remote), true)
        )
    }

    @Test
    fun unavailableEngineIsRejected() {
        val local = candidate(
            "local",
            AiExecutionLocation.LOCAL,
            available = false
        )

        assertNull(SentinelAiEngineRouter.select(listOf(local), false))
    }

    @Test
    fun incompatibleModelIsRejected() {
        val local = candidate(
            "local",
            AiExecutionLocation.LOCAL,
            supportsText = false
        )

        assertNull(SentinelAiEngineRouter.select(listOf(local), false))
    }

    @Test
    fun blankIdentifiersAreRejected() {
        val local = candidate("", AiExecutionLocation.LOCAL)

        assertNull(SentinelAiEngineRouter.select(listOf(local), false))
    }

    @Test
    fun selectionDoesNotDependOnInputOrder() {
        val a = candidate("alpha", AiExecutionLocation.LOCAL)
        val b = candidate("beta", AiExecutionLocation.LOCAL)

        assertEquals(
            a,
            SentinelAiEngineRouter.select(listOf(b, a), false)
        )
    }
}
