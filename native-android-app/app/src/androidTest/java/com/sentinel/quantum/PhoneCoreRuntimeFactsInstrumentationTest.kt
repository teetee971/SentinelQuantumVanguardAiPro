package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
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
    fun carrierEnvironmentMatchesObservableAccountsAndSubscriptions() {
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val phoneStateEffective = PermissionChecker.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PermissionChecker.PERMISSION_GRANTED
        val expected = phoneStateGranted && phoneStateEffective && runCatching {
            context.getSystemService(TelecomManager::class.java)
                ?.callCapablePhoneAccounts.orEmpty().isNotEmpty() &&
                context.getSystemService(SubscriptionManager::class.java)
                    ?.activeSubscriptionInfoList.orEmpty()
                    .any { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        }.getOrDefault(false)
        assertEquals(expected, PhoneCoreRuntimeFacts.hasOperationalCarrierEnvironment(context))
    }

    @Test
    fun callScreeningActivationMatchesCurrentPlatformContract() {
        val screeningState = CallScreeningActivationPolicy.read(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = requireNotNull(context.getSystemService(RoleManager::class.java))
            val expected = when {
                !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ->
                    CallScreeningActivationPolicy.State.UNAVAILABLE
                manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ->
                    CallScreeningActivationPolicy.State.HELD
                else -> CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD
            }
            assertEquals(expected, screeningState)
        } else {
            val defaultDialerHeld = context.getSystemService(TelecomManager::class.java)
                ?.defaultDialerPackage == context.packageName
            assertEquals(
                if (defaultDialerHeld) CallScreeningActivationPolicy.State.HELD
                else CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD,
                screeningState
            )
            val request = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            assertNotNull(context.packageManager.resolveActivity(request, 0))
        }
    }

    @Test
    fun roleRuntimeFactsMatchPlatformTruthOnCurrentApi() {
        val facts = PhoneCoreRuntimeFacts.read(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            assertTrue(facts.dialerRoleAvailable)
            val smsMessagingAvailable = context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING
            )
            assertEquals(smsMessagingAvailable, facts.smsRoleAvailable)
            assertEquals(
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName,
                facts.dialerRoleHeld
            )
            assertEquals(
                smsMessagingAvailable &&
                    runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }
                        .getOrDefault(false),
                facts.smsRoleHeld
            )
            assertEquals(facts.dialerRoleHeld, facts.callScreeningRoleHeld)
            assertTrue(facts.callScreeningRoleAvailable)
        } else {
            val roleManager = context.getSystemService(RoleManager::class.java)
            assertNotNull(roleManager)
            requireNotNull(roleManager)

            val dialerAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
            val dialerHeld = dialerAvailable && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            val smsAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
            val smsHeld = smsAvailable && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            val screeningAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            val screeningHeld = screeningAvailable && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

            assertEquals(dialerAvailable, facts.dialerRoleAvailable)
            assertEquals(dialerHeld, facts.dialerRoleHeld)
            assertEquals(smsAvailable, facts.smsRoleAvailable)
            assertEquals(smsHeld, facts.smsRoleHeld)
            assertEquals(screeningAvailable, facts.callScreeningRoleAvailable)
            assertEquals(screeningHeld, facts.callScreeningRoleHeld)
        }
    }
}
