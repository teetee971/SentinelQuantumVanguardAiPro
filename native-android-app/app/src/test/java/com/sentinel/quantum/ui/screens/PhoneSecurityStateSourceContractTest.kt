package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SDK-free guard that the editable phone number survives Activity recreation. */
class PhoneSecurityStateSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
    ).readText()

    @Test
    fun phoneNumberUsesSaveableStateAcrossActivityRecreation() {
        assertTrue(
            "PhoneSecurityScreen must use rememberSaveable for the editable phone number",
            source.contains("var phoneNumber by rememberSaveable { mutableStateOf(\"\") }")
        )
        assertFalse(
            "PhoneSecurityScreen must not keep the editable phone number only in remember state",
            source.contains("var phoneNumber by remember { mutableStateOf(\"\") }")
        )
    }
}
