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
        val store = OfflineMeshGattAndroidTrustStore(context).also { it.resetStorage() }
        val key = keyPair()
        val pending = store.requestApproval(key.public.encoded, 100L)
        store.approve(pending.stableId, 101L)
        assertTrue(OfflineMeshGattAndroidTrustStore(context).isTrusted(key.public.encoded))

        store.revoke(pending.stableId, 102L)
        assertFalse(OfflineMeshGattAndroidTrustStore(context).isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.REVOKED, store.records().single().state)
        store.resetStorage()
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
            OfflineMeshGattAndroidTrustStore(context).resetStorage()
        }
    }

    @Test
    fun restoringAnOlderAuthenticatedTrustFileCannotRestoreRevokedTrust() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val store = OfflineMeshGattAndroidTrustStore(context).also { it.resetStorage() }
        val file = context.noBackupFilesDir.resolve("offline_mesh_trust_v1.bin")
        val key = keyPair()
        val pending = store.requestApproval(key.public.encoded, 300L)
        store.approve(pending.stableId, 301L)
        val oldBytes = file.readBytes()
        store.revoke(pending.stableId, 302L)
        file.writeBytes(oldBytes)
        try {
            assertFalse(OfflineMeshGattAndroidTrustStore(context).isTrusted(key.public.encoded))
            assertEquals(OfflineMeshTrustState.ERROR, store.observe(key.public.encoded, 303L).state)
        } finally {
            store.resetStorage()
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
