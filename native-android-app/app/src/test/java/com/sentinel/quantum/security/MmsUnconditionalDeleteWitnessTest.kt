package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Witness of the pre-fix provider mutations. A passing run means neither MMS store
 * deletes or updates a root with a null selection.
 */
class MmsUnconditionalDeleteWitnessTest {
    @Test fun outgoingStoreDoesNotDeleteOrUpdateWithoutSelection() {
        val source = source("com/sentinel/quantum/security/MmsConversationStore.kt")
        assertFalse(source.contains("contentResolver.delete(uri, null, null)"))
        assertFalse(source.contains("contentResolver.update(uri, values, null, null)"))
        assertTrue(source.contains("authorizeOutgoingDelete"))
        assertTrue(source.contains("authorizeOutgoingFinalize"))
        assertTrue(source.contains("repairAction"))
    }

    @Test fun incomingStoreDoesNotDeleteWithoutSelection() {
        val source = source("com/sentinel/quantum/security/IncomingMmsConversationStore.kt")
        assertFalse(source.contains("contentResolver.delete(uri, null, null)"))
        assertTrue(source.contains("authorizeIncomingDelete"))
    }

    private fun source(relative: String): String {
        val file = File("src/main/java/$relative")
        return file.readText()
    }
}
