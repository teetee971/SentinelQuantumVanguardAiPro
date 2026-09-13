package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLinkAnalyzerTest {
    private val analyzer = SmsLinkAnalyzer()

    @Test
    fun plainMessageWithoutLinksIsSafe() {
        assertFalse(analyzer.analyzeSmsContent("Bonjour, on se voit demain ?"))
    }

    @Test
    fun httpLinkIsFlaggedDangerous() {
        assertTrue(analyzer.analyzeSmsContent("Votre colis est bloqué, cliquez ici http://192.0.2.10/livraison"))
    }

    @Test
    fun spamPatternIsFlaggedDangerous() {
        assertTrue(analyzer.analyzeSmsContent("Urgent : votre compte est bloqué, effectuez un virement"))
    }

    @Test
    fun blankMessageReturnsFalse() {
        assertFalse(analyzer.analyzeSmsContent("   "))
        assertFalse(analyzer.analyzeSmsContent(""))
    }
}
