package com.sentinel.quantum.wearable.security

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import java.security.GeneralSecurityException

object WearableHpkeLimits {
    const val MAX_PUBLIC_KEY_BYTES = 4 * 1024
    const val MAX_CONTEXT_INFO_BYTES = 4 * 1024
    const val MAX_PLAINTEXT_BYTES = 64 * 1024
    const val MAX_CIPHERTEXT_BYTES = 128 * 1024
}

class WearableHpkePublicKey private constructor(
    private val serializedKeyset: ByteArray
) {
    fun encoded(): ByteArray = serializedKeyset.copyOf()

    internal fun keysetHandle(): KeysetHandle =
        TinkProtoKeysetFormat.parseKeysetWithoutSecret(serializedKeyset)

    companion object {
        @JvmStatic
        @Throws(GeneralSecurityException::class)
        fun fromEncoded(serialized: ByteArray): WearableHpkePublicKey {
            require(serialized.isNotEmpty()) { "HPKE public key must not be empty" }
            require(serialized.size <= WearableHpkeLimits.MAX_PUBLIC_KEY_BYTES) {
                "HPKE public key exceeds maximum size"
            }
            WearableHpkeRuntime.ensureRegistered()
            val copy = serialized.copyOf()
            val handle = TinkProtoKeysetFormat.parseKeysetWithoutSecret(copy)
            handle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
            return WearableHpkePublicKey(copy)
        }
    }
}

class WearableHpkeSender private constructor(
    private val encryptor: HybridEncrypt
) {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray {
        require(plaintext.size <= WearableHpkeLimits.MAX_PLAINTEXT_BYTES) {
            "HPKE plaintext exceeds maximum size"
        }
        require(contextInfo.size <= WearableHpkeLimits.MAX_CONTEXT_INFO_BYTES) {
            "HPKE context info exceeds maximum size"
        }
        val ciphertext = encryptor.encrypt(plaintext.copyOf(), contextInfo.copyOf())
        require(ciphertext.size <= WearableHpkeLimits.MAX_CIPHERTEXT_BYTES) {
            "HPKE ciphertext exceeds maximum size"
        }
        return ciphertext
    }

    companion object {
        @JvmStatic
        @Throws(GeneralSecurityException::class)
        fun fromPublicKey(publicKey: WearableHpkePublicKey): WearableHpkeSender {
            WearableHpkeRuntime.ensureRegistered()
            val primitive = publicKey.keysetHandle()
                .getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
            return WearableHpkeSender(primitive)
        }
    }
}

class WearableHpkeKeyPair private constructor(
    private val privateKeysetHandle: KeysetHandle,
    val publicKey: WearableHpkePublicKey
) {
    val profileName: String
        get() = PROFILE_NAME

    @Throws(GeneralSecurityException::class)
    fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray {
        require(ciphertext.isNotEmpty()) { "HPKE ciphertext must not be empty" }
        require(ciphertext.size <= WearableHpkeLimits.MAX_CIPHERTEXT_BYTES) {
            "HPKE ciphertext exceeds maximum size"
        }
        require(contextInfo.size <= WearableHpkeLimits.MAX_CONTEXT_INFO_BYTES) {
            "HPKE context info exceeds maximum size"
        }
        WearableHpkeRuntime.ensureRegistered()
        val decryptor = privateKeysetHandle
            .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
        return decryptor.decrypt(ciphertext.copyOf(), contextInfo.copyOf())
    }

    companion object {
        const val PROFILE_NAME = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"

        @JvmStatic
        @Throws(GeneralSecurityException::class)
        fun generate(): WearableHpkeKeyPair {
            WearableHpkeRuntime.ensureRegistered()
            val privateHandle = KeysetHandle.newBuilder()
                .addEntry(
                    KeysetHandle.generateEntryFromParametersName(PROFILE_NAME)
                        .withRandomId()
                        .makePrimary()
                )
                .build()
            val publicHandle = privateHandle.publicKeysetHandle
            val serializedPublic = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(publicHandle)
            require(serializedPublic.size <= WearableHpkeLimits.MAX_PUBLIC_KEY_BYTES) {
                "Generated HPKE public key exceeds maximum size"
            }
            return WearableHpkeKeyPair(
                privateKeysetHandle = privateHandle,
                publicKey = WearableHpkePublicKey.fromEncoded(serializedPublic)
            )
        }
    }
}

private object WearableHpkeRuntime {
    @Volatile
    private var registered = false

    @Synchronized
    fun ensureRegistered() {
        if (registered) return
        HybridConfig.register()
        registered = true
    }
}
