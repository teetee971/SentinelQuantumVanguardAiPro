package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreFrenchLabelsTest {
    @Test fun callDecisionCodesAreFrenchForUsers() {
        assertEquals("Autorisé", PhoneCoreFrenchLabels.action("ALLOW"))
        assertEquals("Bloqué", PhoneCoreFrenchLabels.action("BLOCK"))
        assertEquals("Mis en sourdine", PhoneCoreFrenchLabels.action("SILENCE"))
        assertEquals("Aucune règle spécifique", PhoneCoreFrenchLabels.source("NONE"))
        assertEquals(
            "Aucune règle de blocage correspondante",
            PhoneCoreFrenchLabels.reason("NO_MATCHING_RULE")
        )
    }

    @Test fun unknownInternalCodesDoNotLeakRawEnglish() {
        assertEquals("État non traduit", PhoneCoreFrenchLabels.action("SOME_NEW_ACTION"))
        assertEquals("Source technique non traduite", PhoneCoreFrenchLabels.source("SOME_SOURCE"))
        assertEquals("Motif technique non traduit", PhoneCoreFrenchLabels.reason("SOME_REASON"))
    }

    @Test fun readinessStatesAreFrench() {
        assertEquals("PRÊT", PhoneCoreFrenchLabels.diagnosticState(PhoneCoreDiagnostics.State.READY))
        assertEquals("LIMITÉ", PhoneCoreFrenchLabels.diagnosticState(PhoneCoreDiagnostics.State.LIMITED))
        assertEquals("BLOQUÉ", PhoneCoreFrenchLabels.diagnosticState(PhoneCoreDiagnostics.State.LOCKED))
        assertEquals("PRÊT", PhoneCoreFrenchLabels.smsState(SmsActivationDiagnostics.State.READY))
    }

    @Test fun smsFindingCodesAreFrenchForUsers() {
        assertEquals("Lien non chiffré", PhoneCoreFrenchLabels.smsFinding("CLEARTEXT_LINK"))
        assertEquals("Lien raccourci", PhoneCoreFrenchLabels.smsFinding("URL_SHORTENER"))
        assertEquals(
            "Langage d’ingénierie sociale détecté",
            PhoneCoreFrenchLabels.smsFinding("SOCIAL_ENGINEERING_LANGUAGE")
        )
        assertEquals("Signal technique à vérifier", PhoneCoreFrenchLabels.smsFinding("NEW_CODE"))
    }

}
