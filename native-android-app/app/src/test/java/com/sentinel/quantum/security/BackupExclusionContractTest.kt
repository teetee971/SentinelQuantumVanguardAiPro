package com.sentinel.quantum.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupExclusionContractTest {
    @Test fun legacyAndModernBackupRulesExcludePrivateStorage() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue(legacy.contains("domain=\"$domain\""))
        }
        assertTrue(modern.contains("domain=\"root\""))
        assertTrue(modern.contains("<exclude domain=\"root\" path=\".\" />"))
    }
}
