package com.sentinel.quantum.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth0LogoutContractTest {
    @Test
    fun logout_is_locally_final_even_when_remote_sso_logout_fails() {
        val controllerSource = File(
            "src/main/java/com/sentinel/quantum/auth/Auth0SessionController.kt"
        ).readText()
        val settingsSource = File(
            "src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt"
        ).readText()

        val logout = controllerSource.substringAfter("fun logout(")
            .substringBefore("private fun Credentials.toSummary")
        val localClear = logout.indexOf("credentialsManager.clearCredentials()")
        val remoteLogout = logout.indexOf("WebAuthProvider.logout(account)")

        assertTrue("local credentials must be cleared before remote SSO logout starts", localClear >= 0)
        assertTrue("local credentials must be cleared before remote SSO logout starts", localClear < remoteLogout)

        val settingsLogout = settingsSource.substringAfter("authController.logout(")
            .substringBefore("enabled = activity != null")
        val remoteFailure = settingsLogout.substringAfter("onFailure =")

        assertTrue(
            "remote logout failure must still leave the local UI signed out",
            remoteFailure.contains("authUser = null")
        )
    }
}
