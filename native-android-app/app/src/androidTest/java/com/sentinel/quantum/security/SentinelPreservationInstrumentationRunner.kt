package com.sentinel.quantum.security

import androidx.test.runner.AndroidJUnitRunner

/**
 * Additional defense only. Replacing this runner on the command line does not
 * authorize the installed package: each test class also installs RefuseInstalledPackageRule.
 */
class SentinelPreservationInstrumentationRunner : AndroidJUnitRunner() {
    override fun onStart() {
        InstalledDataGuard.assertSafeToRun(targetContext, context)
        super.onStart()
    }
}
