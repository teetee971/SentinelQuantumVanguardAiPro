package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InCallConferenceUiPolicyTest {
    @Test fun labelsAreBoundedAndDoNotRequirePhoneNumbers() {
        val presented = InCallConferenceUiPolicy.present(
            listOf(
                InCallConferenceUiPolicy.Target("a", " Alice "),
                InCallConferenceUiPolicy.Target("b", null)
            )
        )
        assertEquals("Alice", presented[0].label)
        assertEquals("Autre appel 2", presented[1].label)
    }

    @Test fun duplicateAndBlankTargetsAreRejected() {
        val presented = InCallConferenceUiPolicy.present(
            listOf(
                InCallConferenceUiPolicy.Target("", "Vide"),
                InCallConferenceUiPolicy.Target("same", "Premier"),
                InCallConferenceUiPolicy.Target("same", "Doublon")
            )
        )
        assertEquals(1, presented.size)
        assertEquals("Premier", presented.single().label)
    }

    @Test fun targetListIsBounded() {
        val presented = InCallConferenceUiPolicy.present(
            (0 until 10).map { InCallConferenceUiPolicy.Target("id-$it", "Appel $it") }
        )
        assertEquals(4, presented.size)
    }
}
