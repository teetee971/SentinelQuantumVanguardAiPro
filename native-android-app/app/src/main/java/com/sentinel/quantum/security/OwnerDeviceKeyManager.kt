package com.sentinel.quantum.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec

/**
 * OWNER device identity backed by AndroidKeyStore.
 *
 * The private key is non-exportable and remains inside AndroidKeyStore. This
 * component intentionally does not authenticate against a server by itself:
 * enrollment, challenge issuance, replay protection, device revocation and
 * OWNER identity binding remain server-side responsibilities.
 */
class OwnerDeviceKeyManager {

    companion object {
        const val KEY_ALIAS = "sentinel_owner_device_identity_v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val EC_CURVE = "secp256r1"
    }

    data class DevicePublicIdentity(
        val algorithm: String,
        val publicKeyBase64: String,
        val certificateChainBase64: List<String>,
        val hardwareBacked: Boolean,
        val strongBoxBacked: Boolean
    )

    /**
     * Creates the OWNER device key if it does not already exist.
     *
     * If a server-issued attestation challenge is provided, it is bound into
     * Android key attestation on supported devices. The challenge must be
     * fresh and single-use; this method does not create or validate it.
     */
    fun ensureKey(attestationChallenge: ByteArray? = null): DevicePublicIdentity {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey(attestationChallenge, preferStrongBox = true)
        }
        return publicIdentity()
    }

    fun publicIdentity(): DevicePublicIdentity {
        val keyStore = loadKeyStore()
        val certificate = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("OWNER device key is not enrolled")
        val chain = keyStore.getCertificateChain(KEY_ALIAS)?.toList() ?: listOf(certificate)
        val privateKey = keyStore.getKey(KEY_ALIAS, null)
            ?: throw IllegalStateException("OWNER device private key is unavailable")

        val keyInfo = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            .getKeySpec(privateKey, KeyInfo::class.java)

        val strongBoxBacked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && keyInfo.isStrongBoxBacked

        return DevicePublicIdentity(
            algorithm = privateKey.algorithm,
            publicKeyBase64 = Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP),
            certificateChainBase64 = chain.map(Certificate::getEncoded)
                .map { Base64.encodeToString(it, Base64.NO_WRAP) },
            hardwareBacked = keyInfo.isInsideSecureHardware,
            strongBoxBacked = strongBoxBacked
        )
    }

    /**
     * Signs a server-issued challenge. The challenge must be validated for
     * freshness and one-time use by the server before the signature is trusted.
     */
    fun signChallenge(challenge: ByteArray): ByteArray {
        require(challenge.size in 16..1024) { "Challenge length is outside the accepted range" }

        val privateKey = loadKeyStore().getKey(KEY_ALIAS, null)
            ?: throw IllegalStateException("OWNER device key is not enrolled")

        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey as java.security.PrivateKey)
            update(challenge)
            sign()
        }
    }

    fun revokeLocalKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun generateKey(attestationChallenge: ByteArray?, preferStrongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (attestationChallenge != null) {
            require(attestationChallenge.size in 16..128) { "Attestation challenge length is outside the accepted range" }
            builder.setAttestationChallenge(attestationChallenge.copyOf())
        }

        if (preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                generator.initialize(builder.build())
                generator.generateKeyPair()
                return
            } catch (_: StrongBoxUnavailableException) {
                // Fall back to the platform hardware-backed keystore when StrongBox is unavailable.
            }
        }

        val fallbackBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (attestationChallenge != null) {
            fallbackBuilder.setAttestationChallenge(attestationChallenge.copyOf())
        }

        generator.initialize(fallbackBuilder.build())
        generator.generateKeyPair()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
