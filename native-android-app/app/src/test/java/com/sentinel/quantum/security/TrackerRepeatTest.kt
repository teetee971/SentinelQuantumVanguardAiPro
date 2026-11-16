package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackerRepeatTest {
    @Test
    fun oneSightingIsNotFollowingAndStatusByteDoesNotChangeIdentity() {
        val first = ByteArray(27)
        first[0] = 0x12
        first[1] = 0x19
        first[2] = 1
        first[6] = 9
        val later = first.copyOf()
        later[2] = 3
        assertEquals(TrackerRepeat.identity(first), TrackerRepeat.identity(later))
        val other = first.copyOf()
        other[6] = 4
        assertNotEquals(TrackerRepeat.identity(first), TrackerRepeat.identity(other))
        assertEquals("Annonce vue une fois. Pas un suivi.", TrackerRepeat.label(1, 0))
        assertEquals(
            "Annonce revue 2 fois sans changement de réseau. Cela peut être un objet qui reste ici.",
            TrackerRepeat.label(2, 1)
        )
        assertEquals(
            "Annonce revue au moins 3 fois sur des réseaux différents. Indice de déplacement, pas une preuve de suivi.",
            TrackerRepeat.label(3, 2)
        )
        assertEquals(null, TrackerRepeat.place("02:00:00:00:00:00"))
    }
}
