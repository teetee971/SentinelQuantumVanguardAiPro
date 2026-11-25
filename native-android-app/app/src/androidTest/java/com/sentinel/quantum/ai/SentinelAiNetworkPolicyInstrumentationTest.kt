package com.sentinel.quantum.ai

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on the target Android OS. XML guards and compilation cannot prove these results. */
@RunWith(AndroidJUnit4::class)
class SentinelAiNetworkPolicyInstrumentationTest {

    companion object {
        @kotlin.jvm.JvmStatic
        @get:org.junit.ClassRule
        val refuseInstalledPackage = com.sentinel.quantum.security.RefuseInstalledPackageRule()
    }

    @Test fun permitsOnlyTheApprovedLoopbackHost() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
        for (host in listOf("example.com", "localhost", "127.0.0.2", "::1", "127.0.0.1.example.com")) {
            assertFalse("Unexpected cleartext allowance for $host", policy.isCleartextTrafficPermitted(host))
        }
    }
}
