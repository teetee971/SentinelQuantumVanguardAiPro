package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkOperationPolicyTest {
    private val authorizedLocalSession = NetworkAuthorizationContext(
        ownsOrAdministersTarget = true,
        explicitTestSession = true,
        localOrIsolatedScope = true
    )

    @Test fun defensiveDiscoveryRemainsAvailableInBothModes() {
        NetworkOperationMode.entries.forEach { mode ->
            assertTrue(NetworkOperationPolicy.permitsDefensiveDiscovery(mode))
        }
    }

    @Test fun defensiveLocalNeverUnlocksActiveProbe() {
        assertFalse(
            NetworkOperationPolicy.permitsActiveProbe(
                NetworkOperationMode.DEFENSIVE_LOCAL,
                authorizedLocalSession
            )
        )
    }

    @Test fun authorizedLabRequiresAllAuthorizationPredicates() {
        assertTrue(
            NetworkOperationPolicy.permitsActiveProbe(
                NetworkOperationMode.AUTHORIZED_LAB,
                authorizedLocalSession
            )
        )

        listOf(
            authorizedLocalSession.copy(ownsOrAdministersTarget = false),
            authorizedLocalSession.copy(explicitTestSession = false),
            authorizedLocalSession.copy(localOrIsolatedScope = false)
        ).forEach { context ->
            assertFalse(
                NetworkOperationPolicy.permitsActiveProbe(
                    NetworkOperationMode.AUTHORIZED_LAB,
                    context
                )
            )
        }
    }

    @Test fun trafficInspectionAndAdversarialSimulationShareTheSameGate() {
        assertTrue(
            NetworkOperationPolicy.permitsTrafficInspection(
                NetworkOperationMode.AUTHORIZED_LAB,
                authorizedLocalSession
            )
        )
        assertTrue(
            NetworkOperationPolicy.permitsAdversarialSimulation(
                NetworkOperationMode.AUTHORIZED_LAB,
                authorizedLocalSession
            )
        )
    }

    @Test fun thirdPartyTargetingIsNeverPermittedByThisPolicy() {
        NetworkOperationMode.entries.forEach { mode ->
            assertFalse(
                NetworkOperationPolicy.permitsThirdPartyTargeting(
                    mode,
                    authorizedLocalSession
                )
            )
        }
    }
}
