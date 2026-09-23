package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLineSelectionPolicyTest {
    @Test fun singleLineIsSelectedAutomatically() {
        val state = CallLineSelectionPolicy.reconcile(listOf("sim1"), null)
        assertEquals("sim1", state.selectedId)
        assertTrue(state.hasUsableLine)
        assertFalse(state.explicitChoiceRequired)
    }

    @Test fun multipleLinesNeverChooseArbitrarily() {
        val state = CallLineSelectionPolicy.reconcile(listOf("sim1", "sim2"), null)
        assertNull(state.selectedId)
        assertTrue(state.hasUsableLine)
        assertTrue(state.explicitChoiceRequired)
    }

    @Test fun previousExplicitChoiceIsKeptOnlyWhileActive() {
        assertEquals(
            "sim2",
            CallLineSelectionPolicy.reconcile(listOf("sim1", "sim2"), "sim2").selectedId
        )
        val removed = CallLineSelectionPolicy.reconcile(listOf("sim1", "sim3"), "sim2")
        assertNull(removed.selectedId)
        assertTrue(removed.explicitChoiceRequired)
    }

    @Test fun noLineFailsClosed() {
        val state = CallLineSelectionPolicy.reconcile(emptyList(), "sim1")
        assertNull(state.selectedId)
        assertFalse(state.hasUsableLine)
        assertFalse(state.explicitChoiceRequired)
    }
}
