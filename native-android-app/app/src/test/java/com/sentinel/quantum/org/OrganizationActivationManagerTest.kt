package com.sentinel.quantum.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationActivationManagerTest {
    @Test
    fun testActivateByCodeSuccess() {
        val result = OrganizationActivationManager.activateByCode("ORG123", "pay_token_xyz")
        assertTrue(result.success)
        assertEquals("ORG-ORG123", result.orgId)
    }
}
