package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainFilterPolicyTest {

    @Test
    fun blocksExactDomainAndSubdomains() {
        val policy = DomainFilterPolicy(setOf("tracker.example"))

        assertEquals(DomainFilterPolicy.Decision.BLOCK, policy.evaluate("tracker.example").decision)
        assertEquals(DomainFilterPolicy.Decision.BLOCK, policy.evaluate("a.b.tracker.example").decision)
        assertEquals("tracker.example", policy.evaluate("a.b.tracker.example").matchedRule)
    }

    @Test
    fun allowlistOverridesBlockRule() {
        val policy = DomainFilterPolicy(
            blockedDomains = setOf("example.com"),
            allowedDomains = setOf("needed.example.com")
        )

        assertEquals(DomainFilterPolicy.Decision.ALLOW, policy.evaluate("needed.example.com").decision)
        assertEquals(DomainFilterPolicy.Decision.BLOCK, policy.evaluate("other.example.com").decision)
    }

    @Test
    fun normalizesCaseAndTrailingDot() {
        val policy = DomainFilterPolicy(setOf("tracker.example"))
        assertEquals(DomainFilterPolicy.Decision.BLOCK, policy.evaluate("TRACKER.EXAMPLE.").decision)
    }

    @Test
    fun rejectsUrlsAndInvalidHostsWithoutBlocking() {
        val policy = DomainFilterPolicy(setOf("tracker.example"))
        val result = policy.evaluate("https://tracker.example/path")
        assertEquals(DomainFilterPolicy.Decision.ALLOW, result.decision)
        assertNull(result.matchedRule)
        assertEquals("INVALID_OR_UNSUPPORTED_HOST", result.reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExcessiveRuleCounts() {
        DomainFilterPolicy((0..DomainFilterPolicy.MAX_RULES).map { "d$it.example" })
    }
}
