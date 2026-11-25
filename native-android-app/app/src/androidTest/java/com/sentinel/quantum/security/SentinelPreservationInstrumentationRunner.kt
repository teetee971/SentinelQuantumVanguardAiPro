package com.sentinel.quantum.security

import androidx.test.runner.AndroidJUnitRunner

/** Stops the suite before any test can write when the installed V25 database is present. */
class SentinelPreservationInstrumentationRunner : AndroidJUnitRunner() {
    override fun onStart() {
        InstalledDataGuard.assertSafeToRun(targetContext)
        super.onStart()
    }
}
