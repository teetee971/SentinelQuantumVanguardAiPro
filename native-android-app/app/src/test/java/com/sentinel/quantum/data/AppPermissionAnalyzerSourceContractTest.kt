package com.sentinel.quantum.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionAnalyzerSourceContractTest {
    @Test
    fun `permission analyzer avoids redundant nullable label chains while preserving fallbacks`() {
        val source = File("src/main/java/com/sentinel/quantum/data/AppPermissionAnalyzer.kt").readText()

        assertFalse(source.contains("applicationInfo.loadLabel(packageManager)?.toString()"))
        assertFalse(source.contains(".loadLabel(packageManager)\n                ?.toString()"))
        assertTrue(source.contains("?: packageName"))
        assertTrue(source.contains("?: permissionName"))
    }
}
