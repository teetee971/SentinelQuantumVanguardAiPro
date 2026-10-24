package com.sentinel.quantum.security

import android.util.Base64
import java.security.MessageDigest

/**
 * AndroidKeyStore bridge for the GATT identity. The owner private key remains
 * inside AndroidKeyStore; only the public X.509 key is sent in the handshake.
 */
object OfflineMeshGattOwnerIdentity {
    fun fromOwnerDeviceKeyManager(
        keyManager: OwnerDeviceKeyManager,
        trustedPeerPublicKeysX509: Collection<ByteArray>
    ): OfflineMeshGattCryptoIdentity = fromOwnerDeviceKeyManager(
        keyManager,
        { trustedPeerPublicKeysX509 }
    )

    /**
     * The provider is evaluated for every handshake, allowing the application
     * to revoke or rotate a peer without rebuilding the identity object.
     */
    fun fromOwnerDeviceKeyManager(
        keyManager: OwnerDeviceKeyManager,
        trustedPeerPublicKeysX509: () -> Collection<ByteArray>
    ): OfflineMeshGattCryptoIdentity {
        val publicKey = Base64.decode(
            keyManager.publicIdentity().publicKeyBase64,
            Base64.NO_WRAP
        )
        return OfflineMeshGattCryptoIdentity.fromSigner(
            publicKeyX509 = publicKey,
            signer = keyManager::signChallenge,
            trusts = { candidate ->
                trustedPeerPublicKeysX509().any { MessageDigest.isEqual(it, candidate) }
            }
        )
    }
}
