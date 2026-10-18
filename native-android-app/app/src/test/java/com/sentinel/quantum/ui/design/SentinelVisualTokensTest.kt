package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color

class SentinelVisualTokensTest {
    @Test
    fun everyStatusHasUniqueSemanticLabelAndVisualRole() {
        val visuals = SentinelStatus.entries.map { it.visual() }

        assertEquals(SentinelStatus.entries.size, visuals.map { it.label }.distinct().size)
        assertTrue(visuals.all { it.label.isNotBlank() })
        assertTrue(visuals.all { it.role in VisualRole.entries })
    }

    @Test
    fun uncertainOrUnavailableStatesNeverUseConfirmedSuccessRole() {
        listOf(SentinelStatus.UNKNOWN, SentinelStatus.LIMITED, SentinelStatus.LOCKED).forEach { status ->
            assertNotEquals(VisualRole.SUCCESS, status.visual().role)
        }
    }

    @Test
    fun badStateUsesDangerRole() {
        assertEquals(VisualRole.DANGER, SentinelStatus.BAD.visual().role)
    }

    @Test
    fun userFacingTextTokensRemainReadable() {
        assertTrue(SentinelVisualTokens.BodyTextSp >= 12f)
        assertTrue(SentinelVisualTokens.CriticalTextSp >= 12f)
    }

    @Test
    fun d1TokensKeepReadableMinimumsAndDistinctStatusRoles() {
        assertTrue(SentinelVisualTokens.CriticalTextSp >= 16f)
        assertEquals(Color(0xFF070B14), SentinelVisualTokens.Background)
        assertEquals(Color(0xFF39C9F4), SentinelVisualTokens.Cyan)
        assertEquals(Color(0xFF25D8A2), SentinelVisualTokens.Success)
        assertNotEquals(SentinelStatus.UNKNOWN.visual().role, SentinelStatus.OK.visual().role)
    }

    @Test
    fun testAndSimulationStatusesAreExplicit() {
        assertEquals("Test", SentinelStatus.TEST.visual().label)
        assertEquals(VisualRole.TEST, SentinelStatus.TEST.visual().role)
        assertEquals("Simulation", SentinelStatus.PURP.visual().label)
        assertEquals(VisualRole.PURP, SentinelStatus.PURP.visual().role)
    }
}
