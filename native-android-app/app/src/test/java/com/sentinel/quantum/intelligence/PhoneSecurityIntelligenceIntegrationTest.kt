package com.sentinel.quantum.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhoneSecurityIntelligenceIntegrationTest {
    @Test
    fun `phone security reads and maps the shared snapshot off the main thread`() {
        val source = phoneSecuritySource()

        assertTrue(source.contains("IntelligenceCoreRuntime.snapshotForNumber"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("IntelligenceSnapshotPresentation.from"))
        assertTrue(source.contains("intelligencePresentation ="))
    }

    @Test
    fun `phone security keeps intelligence diagnostic only`() {
        val source = phoneSecuritySource()

        assertTrue(!source.contains("ScreeningProjection.from"))
        assertTrue(!source.contains("enforcementAuthorized"))
    }

    private fun phoneSecuritySource(): String {
        val packagePath = "com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
        val candidates = listOf(
            File("src/main/java/$packagePath"),
            File("app/src/main/java/$packagePath"),
            File("native-android-app/app/src/main/java/$packagePath")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error(
                "PhoneSecurityScreen.kt not found from ${File(".").absolutePath}; " +
                    "checked ${candidates.joinToString { it.path }}"
            )
        return source.readText()
    }
}
