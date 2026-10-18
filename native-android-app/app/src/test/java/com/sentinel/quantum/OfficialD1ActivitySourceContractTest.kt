package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialD1ActivitySourceContractTest {
    private fun source(name: String): String = File(
        "src/main/java/com/sentinel/quantum/$name.kt"
    ).readText()

    @Test
    fun contactsUseTheOfficialContactOrbit() {
        val source = source("SentinelDialerActivity")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.CONTACT_ORBIT"))
        assertTrue(source.contains("ContactsContract Android") || source.contains("répertoire"))
    }

    @Test
    fun callerIdUsesTheOfficialNumberIdentityScene() {
        val source = source("CallerIdActivity")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.NUMBER_IDENTITY"))
    }

    @Test
    fun messagesUseTheOfficialSecureMessagesScene() {
        val source = source("SmsComposeActivity")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.SECURE_MESSAGES"))
        assertTrue(source.contains("VOICE_SESSION_HTTP_503") || source.contains("anti"))
    }

    @Test
    fun phoneCoreDiagnosticUsesTheLayeredDeviceScene() {
        val source = source("PhoneCoreDiagnosticActivity")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.DEVICE_ANALYSIS"))
    }
}
