package com.sentinel.quantum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime-only qualification of the Phone Core truth model.
 *
 * These checks deliberately do not grant permissions or roles. They prove that the application
 * can read the real Android state on the emulator and that derived readiness remains fail-closed.
 */
@RunWith(AndroidJUnit4::class)
class PhoneCoreRuntimeFactsInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test
    fun runtimeFactsRespectRoleAndReadinessInvariants() {
        val facts = PhoneCoreRuntimeFacts.read(context)

        assertFalse(facts.dialerRoleHeld && !facts.dialerRoleAvailable)
        assertFalse(facts.callScreeningRoleHeld && !facts.callScreeningRoleAvailable)
        assertFalse(facts.smsRoleHeld && !facts.smsRoleAvailable)
        assertFalse(facts.smsRuntimePermissionsReady && !facts.smsRoleHeld)

        val nextStep = PhoneCoreSetupWizardStore.nextStep(facts)
        val ready = PhoneCoreSetupWizardStore.softwarePrerequisitesReady(facts)
        assertEquals(ready, nextStep == PhoneCoreSetupWizardStore.Step.COMPLETE)

        if (!ready) {
            assertTrue(
                PhoneCoreSetupWizardStore.shouldOpenSetup(
                    persistedCompleted = true,
                    facts = facts
                )
            )
        }
    }

    @Test
    fun carrierEnvironmentCannotBecomeReadyWithoutPhoneStatePermission() {
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!phoneStateGranted) {
            assertFalse(PhoneCoreRuntimeFacts.hasOperationalCarrierEnvironment(context))
        }
    }

    @Test
    fun legacyCallScreeningActivationUsesResolvableDefaultDialerContract() {
        if (Build.VERSION.SDK_INT !in 24..28) return

        val screeningState = CallScreeningActivationPolicy.read(context)
        assertTrue(
            screeningState == CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD ||
                screeningState == CallScreeningActivationPolicy.State.HELD
        )

        val request = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName
            )
        assertNotNull(context.packageManager.resolveActivity(request, 0))
    }
}
