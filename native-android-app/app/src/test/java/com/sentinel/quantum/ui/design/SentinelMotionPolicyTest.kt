package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelMotionPolicyTest {
    @Test
    fun zeroAnimatorScaleUsesStaticDecoration() {
        assertEquals(MotionLevel.STATIC, SentinelMotionPolicy.resolve(0f, powerSave = false))
        assertEquals(MotionLevel.STATIC, SentinelMotionPolicy.resolve(0f, powerSave = true))
    }

    @Test
    fun powerSaveNeverUsesFullMotion() {
        listOf(
            -1f, 0f, 0.25f, 0.5f, 1f, 10f, Float.MAX_VALUE, Float.NaN,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY
        ).forEach { scale ->
            assertNotEquals(
                "Power save at scale $scale",
                MotionLevel.FULL,
                SentinelMotionPolicy.resolve(scale, powerSave = true)
            )
        }
        assertEquals(MotionLevel.REDUCED, SentinelMotionPolicy.resolve(1f, powerSave = true))
    }

    @Test
    fun normalAnimatorScaleAllowsFullMotion() {
        listOf(0.5f, 1f, 2f, Float.MAX_VALUE).forEach { scale ->
            assertEquals(MotionLevel.FULL, SentinelMotionPolicy.resolve(scale, powerSave = false))
        }
    }

    @Test
    fun everyMotionLevelKeepsPrimaryContentVisible() {
        assertTrue(MotionLevel.entries.all { it.rendersPrimaryContent })
    }

    @Test
    fun invalidAnimatorScalesFallBackToStatic() {
        listOf(-1f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach { scale ->
            assertEquals(MotionLevel.STATIC, SentinelMotionPolicy.resolve(scale, powerSave = false))
        }
    }

    @Test
    fun lowAnimatorScaleReducesDecorativeMotion() {
        listOf(Float.MIN_VALUE, 0.25f, 0.499f).forEach { scale ->
            assertEquals(MotionLevel.REDUCED, SentinelMotionPolicy.resolve(scale, powerSave = false))
        }
    }
}
