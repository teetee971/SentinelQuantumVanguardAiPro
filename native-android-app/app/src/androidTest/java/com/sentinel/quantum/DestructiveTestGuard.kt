package com.sentinel.quantum

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule

/** Metadata/argument reads only. Never modifies settings, permissions, roles or app data. */
object DestructiveTestGuard {
    private const val ARG = "sentinelAllowDestructive"

    fun isDisposableDevice(): Boolean = DisposableDevicePolicy.permits(
        Build.FINGERPRINT, Build.HARDWARE, Build.PRODUCT,
        InstrumentationRegistry.getArguments().getString(ARG)
    )

    fun assumeDisposableDevice() = assumeTrue(DisposableDeviceRule.SKIP_MESSAGE, isDisposableDevice())
}

class DestructiveTestRule : TestRule by DisposableDeviceRule(DestructiveTestGuard::isDisposableDevice)
