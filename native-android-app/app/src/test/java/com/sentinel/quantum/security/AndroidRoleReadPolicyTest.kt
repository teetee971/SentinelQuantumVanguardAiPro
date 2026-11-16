package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRoleReadPolicyTest {
    @Test
    fun `boolean role reads fail closed on platform errors`() {
        assertFalse(AndroidRoleReadPolicy.readBoolean { throw SecurityException("denied") })
        assertFalse(AndroidRoleReadPolicy.readBoolean { throw IllegalStateException("framework") })
    }

    @Test
    fun `boolean role reads preserve valid results`() {
        assertTrue(AndroidRoleReadPolicy.readBoolean { true })
        assertFalse(AndroidRoleReadPolicy.readBoolean { false })
    }

    @Test
    fun `nullable role reads fail closed on platform errors`() {
        assertNull(AndroidRoleReadPolicy.readOrNull<String> { throw SecurityException("denied") })
        assertNull(AndroidRoleReadPolicy.readOrNull<String> { throw IllegalStateException("framework") })
    }

    @Test
    fun `nullable role reads preserve successful values`() {
        assertEquals("ok", AndroidRoleReadPolicy.readOrNull { "ok" })
        assertNull(AndroidRoleReadPolicy.readOrNull<String> { null })
    }
}
