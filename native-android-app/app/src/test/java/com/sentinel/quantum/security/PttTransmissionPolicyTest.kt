package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PttTransmissionPolicyTest {
    private val ready = PttTransmissionInputs(true, true, true, PttLinkState.CONNECTED, false)

    @Test fun onlyReadySessionCanTransmit() {
        assertEquals(PttTransmissionDecision.Allowed, PttTransmissionPolicy.evaluate(ready))
    }

    @Test fun missingMicrophonePermissionIsBlocked() {
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.MICROPHONE_PERMISSION_MISSING),
            PttTransmissionPolicy.evaluate(ready.copy(microphonePermissionGranted = false))
        )
    }

    @Test fun missingAuthenticationIsBlocked() {
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.AUTHENTICATION_REQUIRED),
            PttTransmissionPolicy.evaluate(ready.copy(authenticated = false))
        )
    }

    @Test fun missingGroupMembershipIsBlocked() {
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.GROUP_MEMBERSHIP_REQUIRED),
            PttTransmissionPolicy.evaluate(ready.copy(groupMembershipVerified = false))
        )
    }

    @Test fun disconnectedTransportIsBlocked() {
        for (state in PttLinkState.entries.filter { it != PttLinkState.CONNECTED }) {
            assertEquals(
                PttTransmissionDecision.Blocked(PttTransmissionBlock.TRANSPORT_UNAVAILABLE),
                PttTransmissionPolicy.evaluate(ready.copy(linkState = state))
            )
        }
    }

    @Test fun simultaneousTransmissionIsBlocked() {
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.DEVICE_BUSY),
            PttTransmissionPolicy.evaluate(ready.copy(anotherTransmissionActive = true))
        )
    }
    @Test fun connectivityDoesNotBypassAuthorization() {
        val input = ready.copy(microphonePermissionGranted = false, authenticated = false, groupMembershipVerified = false)
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.MICROPHONE_PERMISSION_MISSING),
            PttTransmissionPolicy.evaluate(input)
        )
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.AUTHENTICATION_REQUIRED),
            PttTransmissionPolicy.evaluate(input.copy(microphonePermissionGranted = true))
        )
        assertEquals(
            PttTransmissionDecision.Blocked(PttTransmissionBlock.GROUP_MEMBERSHIP_REQUIRED),
            PttTransmissionPolicy.evaluate(input.copy(microphonePermissionGranted = true, authenticated = true))
        )
    }
}
