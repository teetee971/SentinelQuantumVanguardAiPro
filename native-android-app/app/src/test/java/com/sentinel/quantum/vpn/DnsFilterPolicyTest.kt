package com.sentinel.quantum.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterPolicyTest {
    @Test fun blocksKnownTrackerAndSubdomains() {
        val policy = DnsFilterPolicy()
        assertTrue(policy.evaluate("doubleclick.net").blocked)
        assertTrue(policy.evaluate("ad.doubleclick.net").blocked)
    }

    @Test fun allowlistOverridesBlocklist() {
        val policy = DnsFilterPolicy(
            blockedDomains = setOf("example.com"),
            allowDomains = setOf("api.example.com")
        )
        assertFalse(policy.evaluate("api.example.com").blocked)
        assertTrue(policy.evaluate("ads.example.com").blocked)
    }

    @Test fun invalidInputFailsOpen() {
        assertFalse(DnsFilterPolicy().evaluate("https://example.com").blocked)
    }
}
