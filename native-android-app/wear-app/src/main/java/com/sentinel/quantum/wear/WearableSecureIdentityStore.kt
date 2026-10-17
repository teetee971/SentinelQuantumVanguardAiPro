package com.sentinel.quantum.wear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WearableIdentityPersistenceState { EMPTY, COMPLETE, CORRUPT }

internal fun classifyWearableIdentityPersistence(
    publicKeyX509: String?,
    privateCiphertext: String?,
    privateIv: String?
): WearableIdentityPersistenceState {
    val present = listOf(publicKeyX509, privateCiphertext, privateIv).count { it != null }
    return when (present) {
        0 -> WearableIdentityPersistenceState.EMPTY
        3 -> WearableIdentityPersistenceState.COMPLETE
        else -> WearableIdentityPersistenceState.CORRUPT
    }
}

interface WearableSigningIdentity {
    val publicKeyX509: ByteArray
    fun sign(message: ByteArray): ByteArray
}

interface WearableIdentityBackend {
    @Throws(GeneralSecurityException::class)
    fun loadOrCreate(): WearableSigningIdentity

    @Throws(GeneralSecurityException::class)
    fun reset()
}

data class WearableIdentitySnapshot(
    val stableId: String,
    val publicKeyX509: ByteArray,
    val fingerprintSha256: String
)

class WearableSecureIdentityStore(
    private val backend: WearableIdentityBackend
) {
    @Throws(GeneralSecurityException::class)
    fun snapshot(): WearableIdentitySnapshot {
        val publicKey = backend.loadOrCreate().publicKeyX509.copyOf()
        require(publicKey.isNotEmpty() && publicKey.size <= MAX_PUBLIC_KEY_BYTES) {
            "wearable Ed25519 public key is invalid"
        }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKey)
            .joinToString("") { "%02x".format(it) }
        return WearableIdentitySnapshot(
            stableId = stableIdForFingerprint(fingerprint),
            publicKeyX509 = publicKey,
            fingerprintSha256 = fingerprint
        )
    }

    @Throws(GeneralSecurityException::class)
    fun sign(message: ByteArray): ByteArray {
        require(message.isNotEmpty()) { "message must not be empty" }
        require(message.size <= MAX_SIGNED_MESSAGE_BYTES) { "message exceeds signing bound" }
        return backend.loadOrCreate().sign(message.copyOf())
    }

    @Throws(GeneralSecurityException::class)
    fun reset() = backend.reset()

    companion object {
        private const val MAX_PUBLIC_KEY_BYTES = 1024
        private const val MAX_SIGNED_MESSAGE_BYTES = 16 * 1024
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        internal fun stableIdForFingerprint(fingerprintSha256: String): String {
            require(fingerprintSha256.matches(SHA256_HEX))
            return "wearable-" + fingerprintSha256.take(32)
        }

        fun forApplicationContext(context: Context): WearableSecureIdentityStore =
            WearableSecureIdentityStore(AndroidKeystoreWrappedEd25519Backend(context.applicationContext))
    }
}

/**
 * AndroidKeyStore does not guarantee an Ed25519 key-pair generator across Sentinel's minSdk.
 * A non-exportable AES-GCM key in AndroidKeyStore therefore wraps the Ed25519 PKCS#8 material.
 * The raw Ed25519 private bytes exist only transiently while encrypting/decrypting and are wiped.
 */
internal class AndroidKeystoreWrappedEd25519Backend(
    context: Context
) : WearableIdentityBackend {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun loadOrCreate(): WearableSigningIdentity = synchronized(lock) {
        readIdentity() ?: createIdentity()
    }

    override fun reset() = synchronized(lock) {
        val cleared = preferences.edit().clear().commit()
        if (!cleared) throw GeneralSecurityException("wearable identity reset persistence failed")
        val keyStore = androidKeyStore()
        if (keyStore.containsAlias(WRAPPING_KEY_ALIAS)) keyStore.deleteEntry(WRAPPING_KEY_ALIAS)
        Unit
    }

    private fun readIdentity(): WearableSigningIdentity? {
        val publicStored = preferences.getString(KEY_PUBLIC_X509, null)
        val encryptedStored = preferences.getString(KEY_PRIVATE_CIPHERTEXT, null)
        val ivStored = preferences.getString(KEY_PRIVATE_IV, null)
        when (classifyWearableIdentityPersistence(publicStored, encryptedStored, ivStored)) {
            WearableIdentityPersistenceState.EMPTY -> return null
            WearableIdentityPersistenceState.CORRUPT ->
                throw GeneralSecurityException("wearable identity persistence is incomplete")
            WearableIdentityPersistenceState.COMPLETE -> Unit
        }

        val publicEncoded: ByteArray
        val encryptedPrivate: ByteArray
        val iv: ByteArray
        try {
            publicEncoded = requireNotNull(publicStored).decodeBase64()
            encryptedPrivate = requireNotNull(encryptedStored).decodeBase64()
            iv = requireNotNull(ivStored).decodeBase64()
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("wearable identity persistence is malformed", e)
        }
        if (publicEncoded.isEmpty() || publicEncoded.size > MAX_STORED_KEY_BYTES ||
            encryptedPrivate.isEmpty() || encryptedPrivate.size > MAX_STORED_KEY_BYTES ||
            iv.size !in 12..32
        ) {
            throw GeneralSecurityException("wearable identity persistence bounds are invalid")
        }

        val privateEncoded = decryptPrivate(encryptedPrivate, iv)
        return try {
            val factory = KeyFactory.getInstance(ED25519)
            val publicKey = factory.generatePublic(X509EncodedKeySpec(publicEncoded))
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateEncoded))
            requireMatchingPair(publicKey, privateKey)
            JcaEd25519Identity(publicKey, privateKey)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("wearable identity persistence is invalid", e)
        } finally {
            privateEncoded.fill(0)
        }
    }

    private fun createIdentity(): WearableSigningIdentity {
        val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
        val privateEncoded = pair.private.encoded
            ?: throw GeneralSecurityException("Ed25519 private key is not encodable")
        require(privateEncoded.isNotEmpty() && privateEncoded.size <= MAX_STORED_KEY_BYTES)
        val publicEncoded = pair.public.encoded
        require(publicEncoded.isNotEmpty() && publicEncoded.size <= MAX_STORED_KEY_BYTES)

        return try {
            val encrypted = encryptPrivate(privateEncoded)
            val committed = preferences.edit()
                .putString(KEY_PUBLIC_X509, publicEncoded.encodeBase64())
                .putString(KEY_PRIVATE_CIPHERTEXT, encrypted.ciphertext.encodeBase64())
                .putString(KEY_PRIVATE_IV, encrypted.iv.encodeBase64())
                .commit()
            if (!committed) throw GeneralSecurityException("wearable identity persistence failed")
            JcaEd25519Identity(pair.public, pair.private)
        } finally {
            privateEncoded.fill(0)
        }
    }

    private data class EncryptedPrivateKey(val ciphertext: ByteArray, val iv: ByteArray)

    private fun encryptPrivate(plaintext: ByteArray): EncryptedPrivateKey {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val ciphertext = cipher.doFinal(plaintext)
        require(ciphertext.size <= MAX_STORED_KEY_BYTES)
        return EncryptedPrivateKey(ciphertext, cipher.iv.copyOf())
    }

    private fun decryptPrivate(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        require(plaintext.size <= MAX_STORED_KEY_BYTES)
        return plaintext
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = androidKeyStore()
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun requireMatchingPair(publicKey: PublicKey, privateKey: PrivateKey) {
        val probe = ByteArray(32).also(SecureRandom()::nextBytes)
        val signer = Signature.getInstance(ED25519)
        signer.initSign(privateKey)
        signer.update(probe)
        val signature = signer.sign()
        val verifier = Signature.getInstance(ED25519)
        verifier.initVerify(publicKey)
        verifier.update(probe)
        if (!verifier.verify(signature)) throw GeneralSecurityException("wearable identity key pair mismatch")
        probe.fill(0)
        signature.fill(0)
    }

    private class JcaEd25519Identity(
        private val publicKey: PublicKey,
        private val privateKey: PrivateKey
    ) : WearableSigningIdentity {
        override val publicKeyX509: ByteArray get() = publicKey.encoded.copyOf()
        override fun sign(message: ByteArray): ByteArray {
            val signer = Signature.getInstance(ED25519)
            signer.initSign(privateKey)
            signer.update(message)
            return signer.sign()
        }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ED25519 = "Ed25519"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MAX_STORED_KEY_BYTES = 4 * 1024
        private const val PREFERENCES_NAME = "sentinel_wearable_secure_identity_v1"
        private const val WRAPPING_KEY_ALIAS = "sentinel_wearable_identity_wrap_aes_v1"
        private const val KEY_PUBLIC_X509 = "public_x509"
        private const val KEY_PRIVATE_CIPHERTEXT = "private_ciphertext"
        private const val KEY_PRIVATE_IV = "private_iv"
    }
}
