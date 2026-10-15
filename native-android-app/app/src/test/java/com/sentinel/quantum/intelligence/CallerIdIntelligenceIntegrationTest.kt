package com.sentinel.quantum.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CallerIdIntelligenceIntegrationTest {
    @Test
    fun `caller id reads intelligence snapshot off the main thread and maps it for presentation`() {
        val source = callerIdSource()

        assertTrue(source.contains("IntelligenceCoreRuntime.snapshotForNumber"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("IntelligenceSnapshotPresentation.from"))
        assertTrue(source.contains("intelligencePresentation = intelligencePresentation"))
    }

    @Test
    fun `caller id does not use intelligence snapshot as an enforcement authority`() {
        val source = callerIdSource()

        assertTrue(!source.contains("ScreeningProjection.from"))
        assertTrue(!source.contains("enforcementAuthorized"))
    }

    private fun callerIdSource(): String {
        val packagePath = "com/sentinel/quantum/CallerIdActivity.kt"
        val candidates = listOf(
            File("src/main/java/$packagePath"),
            File("app/src/main/java/$packagePath"),
            File("native-android-app/app/src/main/java/$packagePath")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error(
                "CallerIdActivity.kt not found from ${File(".").absolutePath}; " +
                    "checked ${candidates.joinToString { it.path }}"
            )
        return source.readText()
    }
}
