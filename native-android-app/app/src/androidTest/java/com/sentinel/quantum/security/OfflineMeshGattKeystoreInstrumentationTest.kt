package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineMeshGattKeystoreInstrumentationTest {
    @Test
    fun keystoreMacBackedTrustPersistsAndRevokesAcrossStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        context.noBackupFilesDir.resolve("offline_mesh_trust_v1.bin").delete()
        val store = OfflineMeshGattAndroidTrustStore(context)
        val key = keyPair()
        val pending = store.requestApproval(key.public.encoded, 100L)
        store.approve(pending.stableId, 101L)
        assertTrue(OfflineMeshGattAndroidTrustStore(context).isTrusted(key.public.encoded))

        store.revoke(pending.stableId, 102L)
        assertFalse(OfflineMeshGattAndroidTrustStore(context).isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.REVOKED, store.records().single().state)
    }

    @Test
    fun corruptedDeviceLocalTrustFileFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val file = context.noBackupFilesDir.resolve("offline_mesh_trust_v1.bin")
        file.writeText("corrupt\nnot-a-mac")
        try {
            val record = OfflineMeshGattAndroidTrustStore(context).observe(keyPair().public.encoded, 200L)
            assertEquals(OfflineMeshTrustState.ERROR, record.state)
        } finally {
            file.delete()
        }
    }

    @Test
    fun ownerIdentitySignsWithoutExposingPrivateKeyAndExplicitRevokeRequiresReenrollment() {
        val manager = OwnerDeviceKeyManager()
        val first = manager.ensureKey()
        val signature = manager.signChallenge(ByteArray(32) { 3 })
        assertTrue(signature.isNotEmpty())
        assertTrue(first.publicKeyBase64.isNotEmpty())

        manager.revokeLocalKey()
        val second = manager.ensureKey()
        assertFalse(first.publicKeyBase64 == second.publicKeyBase64)
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
