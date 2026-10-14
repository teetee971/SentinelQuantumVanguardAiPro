package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelMotionPolicyTest {
    @Test
    fun zeroAnimatorScaleUsesStaticDecoration() {
        assertEquals(MotionLevel.STATIC, SentinelMotionPolicy.resolve(0f, powerSave = false))
    }

    @Test
    fun powerSaveNeverUsesFullMotion() {
        assertNotEquals(MotionLevel.FULL, SentinelMotionPolicy.resolve(1f, powerSave = true))
    }

    @Test
    fun normalAnimatorScaleAllowsFullMotion() {
        assertEquals(MotionLevel.FULL, SentinelMotionPolicy.resolve(1f, powerSave = false))
    }

    @Test
    fun everyMotionLevelKeepsPrimaryContentVisible() {
        assertTrue(MotionLevel.entries.all { it.rendersPrimaryContent })
    }
}
