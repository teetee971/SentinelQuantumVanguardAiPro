package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallScreeningActivationPolicyTest {
    @Test fun api23IsUnsupportedEvenWhenOtherSignalsAreTrue() {
        assertEquals(
            CallScreeningActivationPolicy.State.UNAVAILABLE,
            CallScreeningActivationPolicy.resolve(
                apiLevel = 23,
                serviceDeclaredEnabled = true,
                roleAvailable = true,
                roleHeld = true,
                defaultDialerHeld = true
            )
        )
    }

    @Test fun api24To28DefaultDialerMeansScreeningIsHeldWhenServiceExists() {
        listOf(24, 26, 28).forEach { api ->
            assertEquals(
                CallScreeningActivationPolicy.State.HELD,
                CallScreeningActivationPolicy.resolve(
                    apiLevel = api,
                    serviceDeclaredEnabled = true,
                    roleAvailable = false,
                    roleHeld = false,
                    defaultDialerHeld = true
                )
            )
        }
    }

    @Test fun api24To28CanBecomeAvailableViaDefaultDialerPath() {
        listOf(24, 26, 28).forEach { api ->
            assertEquals(
                CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD,
                CallScreeningActivationPolicy.resolve(
                    apiLevel = api,
                    serviceDeclaredEnabled = true,
                    roleAvailable = false,
                    roleHeld = false,
                    defaultDialerHeld = false
                )
            )
        }
    }

    @Test fun missingOrDisabledServiceIsUnavailableOnEverySupportedGeneration() {
        listOf(24, 28, 29, 36).forEach { api ->
            assertEquals(
                CallScreeningActivationPolicy.State.UNAVAILABLE,
                CallScreeningActivationPolicy.resolve(
                    apiLevel = api,
                    serviceDeclaredEnabled = false,
                    roleAvailable = true,
                    roleHeld = true,
                    defaultDialerHeld = true
                )
            )
        }
    }

    @Test fun api29PlusUsesRoleManagerTruth() {
        assertEquals(
            CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD,
            CallScreeningActivationPolicy.resolve(
                apiLevel = 29,
                serviceDeclaredEnabled = true,
                roleAvailable = true,
                roleHeld = false,
                defaultDialerHeld = true
            )
        )
        assertEquals(
            CallScreeningActivationPolicy.State.HELD,
            CallScreeningActivationPolicy.resolve(
                apiLevel = 36,
                serviceDeclaredEnabled = true,
                roleAvailable = true,
                roleHeld = true,
                defaultDialerHeld = false
            )
        )
        assertEquals(
            CallScreeningActivationPolicy.State.UNAVAILABLE,
            CallScreeningActivationPolicy.resolve(
                apiLevel = 36,
                serviceDeclaredEnabled = true,
                roleAvailable = false,
                roleHeld = false,
                defaultDialerHeld = true
            )
        )
    }
}
