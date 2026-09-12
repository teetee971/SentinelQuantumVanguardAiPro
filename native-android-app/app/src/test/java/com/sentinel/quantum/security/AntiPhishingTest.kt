package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiPhishingTest {

    @Test
    fun testSmsPhishingDetection_WithSuspiciousUrl() {
        val extractor = EmailIocExtractor()
        // Scénario d'attaque type : Message SMS ou mail contenant un vecteur HTTP
        val suspiciousInput = "Alerte Sécurité: Votre compte a été suspendu. Vérifiez d'urgence sur http://secure-bank-login.com"
        
        val results = extractor.extractIocs(suspiciousInput)
        
        // Le filtre doit impérativement intercepter la menace
        assertTrue("Le filtre anti-phishing doit détecter le vecteur d'attaque HTTP", results.isNotEmpty())
        assertEquals("Suspicious Link Vector", results.first())
    }

    @Test
    fun testSmsPhishingDetection_WithCleanMessage() {
        val extractor = EmailIocExtractor()
        val cleanInput = "Votre code de virement bancaire temporaire est 483956. Ne le partagez jamais."
        
        val results = extractor.extractIocs(cleanInput)
        
        // Le filtre ne doit générer aucun faux positif sur des messages légitimes
        assertTrue("Un message sain ne doit lever aucun indicateur de compromission", results.isEmpty())
    }
}
