package com.sentinel.quantum

import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** The assumption wraps the whole JUnit statement, including @Before and @After. */
class DisposableDeviceRule(private val permits: () -> Boolean) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            assumeTrue(SKIP_MESSAGE, permits())
            base.evaluate()
        }
    }
    companion object {
        const val SKIP_MESSAGE = "SKIP: destructive test, non-disposable device (emulator or -e sentinelAllowDestructive true required)"
    }
}
