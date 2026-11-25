package com.sentinel.quantum.security

import android.content.Context
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Package identity only. This object does not open SQLite, read a fixture,
 * or delete anything. Database state is not an input.
 */
internal object InstalledDataGuard {
    fun assertSafeToRun(target: Context, instrumentation: Context) {
        val targetPackage = target.packageName
        val testPackage = instrumentation.packageName
        check(
            SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
                targetPackage,
                testPackage
            )
        ) { "REFUSING_TO_TOUCH_INSTALLED_V25_PACKAGE target=$targetPackage test=$testPackage" }
    }
}

/** Runs before @Before, @After and the test body. Not a substitute for the per-test call sites. */
class RefuseInstalledPackageRule : TestRule {
    override fun apply(base: Statement, description: org.junit.runner.Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                InstalledDataGuard.assertSafeToRun(instrumentation.targetContext, instrumentation.context)
                base.evaluate()
            }
        }
}
