package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSecurityPresentationTest {
    private fun healthy() = HomeComponentFacts(
        implemented = true,
        customerAvailable = true,
        evidenceObserved = true,
        condition = HomeCondition.HEALTHY
    )

    @Test
    fun unavailableAndroidRoleIsLimitedWhileAnAvailableMissingRoleIsRemediable() {
        listOf(false, true).forEach { locked ->
            val state = HomeSecurityPresentation.from(
                HomeRuntimeFacts(
                    healthy().copy(requiredRolesAvailable = false, locked = locked),
                    healthy(), healthy(), healthy()
                )
            )
            assertEquals(SentinelStatus.LIMITED, state.modules.first().status)
            assertEquals("Rôle Android indisponible sur cet appareil", state.modules.first().detail)
            assertEquals(SentinelStatus.LIMITED, state.overallStatus)
            assertEquals("Phone Core · Rôle Android indisponible sur cet appareil", state.priorityMessage)
        }
        val remediable = HomeSecurityPresentation.from(
            HomeRuntimeFacts(healthy().copy(locked = true), healthy(), healthy(), healthy())
        )
        assertEquals(SentinelStatus.LOCKED, remediable.modules.first().status)
        assertEquals("Action, rôle ou permission requis", remediable.modules.first().detail)
    }

    @Test
    fun componentHealthNeverInventsAnActivityHistoryOrAnEmptyHistoryClaim() {
        listOf(
            healthy(),
            healthy().copy(evidenceObserved = false),
            healthy().copy(condition = HomeCondition.DANGER),
            healthy().copy(customerAvailable = false, locked = true)
        ).forEach { facts ->
            val state = HomeSecurityPresentation.from(HomeRuntimeFacts(facts, facts, facts, facts))
            assertEquals(
                "Activité récente indisponible : aucun historique d’événements fourni à l’accueil.",
                state.activitySummary
            )
        }
    }

    @Test
    fun unavailableCapabilityTakesPrecedenceOverAMissingRole() {
        listOf(
            healthy().copy(customerAvailable = false, locked = true),
            healthy().copy(implemented = false, locked = true)
        ).forEach { phoneCore ->
            val state = HomeSecurityPresentation.from(
                HomeRuntimeFacts(phoneCore, healthy(), healthy(), healthy())
            )
            assertEquals(SentinelStatus.LIMITED, state.modules.first().status)
            assertEquals(SentinelStatus.LIMITED, state.overallStatus)
            assertTrue(state.priorityMessage.contains(state.modules.first().detail))
            assertNotEquals("Action, rôle ou permission requis", state.modules.first().detail)
        }
    }

    @Test
    fun allExplicitlyHealthyComponentsCanProduceOverallOk() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy(),
                vpn = healthy(),
                network = healthy(),
                collectiveDefense = healthy()
            )
        )

        assertEquals(SentinelStatus.OK, state.overallStatus)
        assertTrue(state.modules.all { it.status == SentinelStatus.OK })
    }

    @Test
    fun missingEvidenceNeverProducesOverallOk() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy(),
                vpn = healthy().copy(evidenceObserved = false),
                network = healthy(),
                collectiveDefense = healthy()
            )
        )

        assertEquals(SentinelStatus.UNKNOWN, state.modules.first { it.id == HomeModuleId.VPN }.status)
        assertNotEquals(SentinelStatus.OK, state.overallStatus)
    }

    @Test
    fun unavailableBackendIsLimitedNotHealthy() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy(),
                vpn = healthy(),
                network = healthy(),
                collectiveDefense = healthy().copy(customerAvailable = false)
            )
        )

        assertEquals(
            SentinelStatus.LIMITED,
            state.modules.first { it.id == HomeModuleId.COLLECTIVE_DEFENSE }.status
        )
        assertNotEquals(SentinelStatus.OK, state.overallStatus)
    }

    @Test
    fun revokedPhoneRoleRequiresActionWithoutInventingDanger() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy().copy(locked = true),
                vpn = healthy(),
                network = healthy(),
                collectiveDefense = healthy()
            )
        )

        assertEquals(SentinelStatus.LOCKED, state.modules.first { it.id == HomeModuleId.PHONE_CORE }.status)
        assertEquals(SentinelStatus.WARN, state.overallStatus)
        assertTrue(state.priorityMessage.contains("Phone Core", ignoreCase = true))
    }

    @Test
    fun degradedComponentKeepsOverallOutOfGreen() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy(),
                vpn = healthy().copy(condition = HomeCondition.DEGRADED),
                network = healthy(),
                collectiveDefense = healthy()
            )
        )

        assertEquals(SentinelStatus.WARN, state.modules.first { it.id == HomeModuleId.VPN }.status)
        assertEquals(SentinelStatus.WARN, state.overallStatus)
    }

    @Test
    fun explicitDangerIsTheOnlyPathToBad() {
        val state = HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = healthy(),
                vpn = healthy(),
                network = healthy().copy(condition = HomeCondition.DANGER),
                collectiveDefense = healthy()
            )
        )

        assertEquals(SentinelStatus.BAD, state.overallStatus)
        assertEquals(SentinelStatus.BAD, state.modules.first { it.id == HomeModuleId.NETWORK }.status)
    }
}
