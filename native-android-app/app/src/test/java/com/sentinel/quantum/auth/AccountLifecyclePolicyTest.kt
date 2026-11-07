package com.sentinel.quantum.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLifecyclePolicyTest {
    @Test fun acceptsExplicitHttpsDeletionRoute() {
        assertTrue(AccountLifecyclePolicy.canStartLogin("https://accounts.example.org/delete-account"))
        assertTrue(AccountLifecyclePolicy.canStartLogin("https://accounts.example.org/delete?app=sentinel"))
    }
    @Test fun missingDeletionRouteBlocksAccountCreation() {
        listOf("", " ", "https://", "https://accounts.example.org", "https://accounts.example.org/").forEach {
            assertFalse(it, AccountLifecyclePolicy.canStartLogin(it))
        }
    }
    @Test fun rejectsUnsafeOrAmbiguousRoutes() {
        listOf("http://example.org/delete", "file:///delete", "javascript:alert(1)",
            "https://user:pass@example.org/delete", "https://@example.org/delete",
            "https://example.org/delete#", "https://example.org/delete#token",
            "https://example.org:0/delete", "https://example.org:70000/delete",
            " https://example.org/delete", "https://example.org/delete\n",
            "https://example.org/\\delete", "https://example.org/%0Adelete").forEach {
            assertFalse(it, AccountLifecyclePolicy.canStartLogin(it))
        }
    }
}
