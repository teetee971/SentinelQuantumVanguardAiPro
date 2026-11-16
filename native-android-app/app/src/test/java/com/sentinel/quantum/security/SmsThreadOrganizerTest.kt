package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsThreadOrganizerTest {
    @Test fun otpContextWinsOverOtherCategories() {
        assertEquals(
            SmsThreadOrganizer.Category.CODE,
            SmsThreadOrganizer.classify("Votre code de vérification est 519243 pour confirmer le paiement.")
        )
    }

    @Test fun deliveryVocabularyMapsToDelivery() {
        assertEquals(
            SmsThreadOrganizer.Category.DELIVERY,
            SmsThreadOrganizer.classify("Votre colis est disponible au point relais.")
        )
    }

    @Test fun transactionVocabularyMapsToTransactions() {
        assertEquals(
            SmsThreadOrganizer.Category.TRANSACTION,
            SmsThreadOrganizer.classify("Paiement par carte accepté. Nouveau solde disponible.")
        )
    }

    @Test fun ordinaryConversationRemainsOther() {
        assertEquals(
            SmsThreadOrganizer.Category.OTHER,
            SmsThreadOrganizer.classify("Bonjour, on se retrouve demain à 14 h ?")
        )
    }

    @Test fun allFilterMatchesEveryConversation() {
        assertTrue(SmsThreadOrganizer.matches(SmsThreadOrganizer.Category.ALL, "texte quelconque"))
    }
}
