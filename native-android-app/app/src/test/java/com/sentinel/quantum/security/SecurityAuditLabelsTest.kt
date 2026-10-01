package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityAuditLabelsTest {
    @Test fun criticalPermissionsUsePlainFrenchLabels() {
        assertEquals(
            "Contacts",
            SecurityAudit.userFacingPermissionLabel("READ_CONTACTS")
        )
        assertEquals(
            "Journal d’appels",
            SecurityAudit.userFacingPermissionLabel("READ_CALL_LOG")
        )
        assertEquals(
            "Envoi de SMS",
            SecurityAudit.userFacingPermissionLabel("SEND_SMS")
        )
        assertEquals(
            "Protection interne des récepteurs Android",
            SecurityAudit.userFacingPermissionLabel("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        )
    }

    @Test fun unknownPermissionStillGetsReadableFallback() {
        assertEquals(
            "Custom Future Capability",
            SecurityAudit.userFacingPermissionLabel("CUSTOM_FUTURE_CAPABILITY")
        )
    }
}
