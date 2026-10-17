package com.sentinel.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface WearablePairingBackend {
    fun load(stableId: String): WearableTrustedIdentity?
    fun save(record: WearableTrustedIdentity)
    fun remove(stableId: String)
}

data class WearableTrustedIdentity(
    val stableId: String,
    val publicKeyX509: ByteArray,
    val keyFingerprintSha256: String,
    val confirmedAtMs: Long
) {
    init {
        require(stableId.isNotBlank())
        require(publicKeyX509.isNotEmpty() && publicKeyX509.size <= MAX_PUBLIC_KEY_BYTES)
        require(keyFingerprintSha256.matches(SHA256_HEX))
        require(confirmedAtMs >= 0)
    }

    companion object {
        internal const val MAX_PUBLIC_KEY_BYTES = 1024
        internal val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}

class WearablePairingStore(
    private val backend: WearablePairingBackend
) {
    fun load(stableId: String): WearableTrustedIdentity? {
        require(stableId.isNotBlank())
        return backend.load(stableId)?.validatedCopy()
    }

    fun confirm(
        stableId: String,
        publicKeyX509: ByteArray,
        keyFingerprintSha256: String,
        confirmedAtMs: Long
    ): WearableTrustedIdentity {
        require(stableId.isNotBlank())
        require(confirmedAtMs >= 0)
        require(publicKeyX509.isNotEmpty() && publicKeyX509.size <= WearableTrustedIdentity.MAX_PUBLIC_KEY_BYTES)
        require(keyFingerprintSha256.matches(WearableTrustedIdentity.SHA256_HEX))
        validateEd25519PublicKey(publicKeyX509)
        val observed = sha256Hex(publicKeyX509)
        require(constantTimeEquals(observed, keyFingerprintSha256)) {
            "wearable identity fingerprint does not match public key"
        }

        val current = backend.load(stableId)
        if (current != null) {
            val sameIdentity = constantTimeEquals(current.keyFingerprintSha256, keyFingerprintSha256) &&
                MessageDigest.isEqual(current.publicKeyX509, publicKeyX509)
            check(sameIdentity) { "existing wearable identity requires explicit re-pairing" }
            return current.validatedCopy()
        }

        val record = WearableTrustedIdentity(
            stableId = stableId,
            publicKeyX509 = publicKeyX509.copyOf(),
            keyFingerprintSha256 = keyFingerprintSha256,
            confirmedAtMs = confirmedAtMs
        )
        backend.save(record)
        return backend.load(stableId)?.validatedCopy()
            ?: throw IllegalStateException("wearable pairing persistence failed")
    }

    fun remove(stableId: String) {
        require(stableId.isNotBlank())
        backend.remove(stableId)
        check(backend.load(stableId) == null) { "wearable pairing removal failed" }
    }

    private fun WearableTrustedIdentity.validatedCopy(): WearableTrustedIdentity {
        validateEd25519PublicKey(publicKeyX509)
        val observed = sha256Hex(publicKeyX509)
        require(constantTimeEquals(observed, keyFingerprintSha256)) {
            "stored wearable identity fingerprint mismatch"
        }
        return copy(publicKeyX509 = publicKeyX509.copyOf())
    }

    companion object {
        fun forApplicationContext(context: Context): WearablePairingStore =
            WearablePairingStore(AndroidKeystoreWearablePairingBackend(context.applicationContext))

        internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        internal fun constantTimeEquals(expected: String, actual: String): Boolean =
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.US_ASCII),
                actual.toByteArray(Charsets.US_ASCII)
            )

        private fun validateEd25519PublicKey(publicKeyX509: ByteArray) {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyX509))
        }
    }
}

internal class AndroidKeystoreWearablePairingBackend(
    context: Context
) : WearablePairingBackend {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun load(stableId: String): WearableTrustedIdentity? = synchronized(lock) {
        val encoded = preferences.getString(preferenceKey(stableId), null) ?: return@synchronized null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size in (IV_BYTES + 1)..MAX_RECORD_BYTES)
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
        val plaintext = decrypt(ciphertext, iv, stableId)
        try {
            decodeRecord(plaintext, stableId)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun save(record: WearableTrustedIdentity) = synchronized(lock) {
        val plaintext = encodeRecord(record)
        try {
            val encrypted = encrypt(plaintext, record.stableId)
            val packed = encrypted.iv + encrypted.ciphertext
            require(packed.size <= MAX_RECORD_BYTES)
            val ok = preferences.edit()
                .putString(preferenceKey(record.stableId), Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit()
            if (!ok) throw GeneralSecurityException("wearable pairing persistence failed")
        } finally {
            plaintext.fill(0)
        }
    }

    override fun remove(stableId: String) = synchronized(lock) {
        preferences.edit().remove(preferenceKey(stableId)).commit()
        Unit
    }

    private data class EncryptedRecord(val ciphertext: ByteArray, val iv: ByteArray)

    private fun encrypt(plaintext: ByteArray, stableId: String): EncryptedRecord {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        cipher.updateAAD(aad(stableId))
        return EncryptedRecord(cipher.doFinal(plaintext), cipher.iv.copyOf())
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray, stableId: String): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(stableId))
        return cipher.doFinal(ciphertext)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
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

    private fun encodeRecord(record: WearableTrustedIdentity): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(RECORD_VERSION)
                out.writeUTF(record.stableId)
                out.writeInt(record.publicKeyX509.size)
                out.write(record.publicKeyX509)
                out.writeUTF(record.keyFingerprintSha256)
                out.writeLong(record.confirmedAtMs)
            }
            buffer.toByteArray()
        }

    private fun decodeRecord(bytes: ByteArray, expectedStableId: String): WearableTrustedIdentity =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == RECORD_VERSION)
            val stableId = input.readUTF()
            require(stableId == expectedStableId)
            val keySize = input.readInt()
            require(keySize in 1..WearableTrustedIdentity.MAX_PUBLIC_KEY_BYTES)
            val publicKey = ByteArray(keySize)
            input.readFully(publicKey)
            val fingerprint = input.readUTF()
            val confirmedAt = input.readLong()
            require(input.available() == 0)
            WearableTrustedIdentity(stableId, publicKey, fingerprint, confirmedAt)
        }

    private fun preferenceKey(stableId: String): String = "pairing_" + WearablePairingStore.sha256Hex(
        stableId.toByteArray(Charsets.UTF_8)
    )

    private fun aad(stableId: String): ByteArray =
        "sentinel-wearable-pairing-v1\u0000$stableId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val PREFERENCES_NAME = "sentinel_wearable_pairings_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAPPING_KEY_ALIAS = "sentinel_wearable_pairings_aes_v1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val RECORD_VERSION = 1
        private const val MAX_RECORD_BYTES = 8 * 1024
    }
}
