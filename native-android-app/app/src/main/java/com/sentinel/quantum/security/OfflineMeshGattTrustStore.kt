package com.sentinel.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.SecretKey

enum class OfflineMeshTrustState {
    UNKNOWN,
    PENDING_APPROVAL,
    TRUSTED,
    REVOKED,
    ERROR
}

data class OfflineMeshTrustRecord(
    val stableId: String,
    val publicKeyX509: ByteArray,
    val state: OfflineMeshTrustState,
    val approvedAtMs: Long?,
    val revokedAtMs: Long?,
    val version: Long,
    val retiredKeyFingerprints: Set<String> = emptySet(),
    val pendingRotationKeyX509: ByteArray? = null
) {
    fun copyPublicKey(): ByteArray = publicKeyX509.copyOf()
}

interface OfflineMeshTrustBackend {
    data class Entry(val payload: String, val mac: ByteArray)

    fun read(): Entry?
    fun write(payload: String, mac: ByteArray): Boolean
}

fun interface OfflineMeshTrustMac {
    fun mac(payload: ByteArray): ByteArray
}

/**
 * Persistent, authenticated trust policy for Offline Mesh identities.
 *
 * The class contains no Bluetooth address and never stores a private key. The
 * backend is responsible for durable, device-local storage; the MAC provider
 * must be backed by AndroidKeyStore in production.
 */
class OfflineMeshTrustStore(
    private val backend: OfflineMeshTrustBackend,
    private val macProvider: OfflineMeshTrustMac
) {
    class StorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
    class PolicyException(message: String) : IllegalStateException(message)

    @Synchronized
    fun observe(publicKeyX509: ByteArray, nowMs: Long): OfflineMeshTrustRecord {
        val key = validatePublicKey(publicKeyX509)
        val snapshot = readSnapshotOrNull() ?: return errorRecord(key, nowMs)
        return find(snapshot.records, fingerprint(key)) ?: OfflineMeshTrustRecord(
            stableId = fingerprint(key),
            publicKeyX509 = key,
            state = OfflineMeshTrustState.UNKNOWN,
            approvedAtMs = null,
            revokedAtMs = null,
            version = snapshot.revision
        )
    }

    @Synchronized
    fun requestApproval(publicKeyX509: ByteArray, nowMs: Long): OfflineMeshTrustRecord {
        val key = validatePublicKey(publicKeyX509)
        val snapshot = readSnapshot()
        val existing = find(snapshot.records, fingerprint(key))
        if (existing?.state == OfflineMeshTrustState.REVOKED) {
            throw PolicyException("REVOKED_IDENTITY_REQUIRES_EXPLICIT_RESET")
        }
        if (existing != null) return existing
        val record = OfflineMeshTrustRecord(
            stableId = fingerprint(key),
            publicKeyX509 = key,
            state = OfflineMeshTrustState.PENDING_APPROVAL,
            approvedAtMs = null,
            revokedAtMs = null,
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = snapshot.records + record))
        return record
    }

    @Synchronized
    fun approve(stableId: String, nowMs: Long): OfflineMeshTrustRecord {
        val snapshot = readSnapshot()
        val existing = snapshot.records.singleOrNull { it.stableId == stableId }
            ?: throw PolicyException("UNKNOWN_IDENTITY")
        if (existing.state == OfflineMeshTrustState.REVOKED) {
            throw PolicyException("REVOKED_IDENTITY_REQUIRES_EXPLICIT_RESET")
        }
        if (existing.state != OfflineMeshTrustState.PENDING_APPROVAL) {
            if (existing.state == OfflineMeshTrustState.TRUSTED) return existing
            throw PolicyException("IDENTITY_NOT_PENDING_APPROVAL")
        }
        val approved = existing.copy(
            state = OfflineMeshTrustState.TRUSTED,
            approvedAtMs = nowMs,
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = replace(snapshot.records, approved)))
        return approved
    }

    @Synchronized
    fun revoke(stableId: String, nowMs: Long): OfflineMeshTrustRecord {
        val snapshot = readSnapshot()
        val existing = snapshot.records.singleOrNull { it.stableId == stableId }
            ?: throw PolicyException("UNKNOWN_IDENTITY")
        val revoked = existing.copy(
            state = OfflineMeshTrustState.REVOKED,
            revokedAtMs = nowMs,
            pendingRotationKeyX509 = null,
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = replace(snapshot.records, revoked)))
        return revoked
    }

    /** Explicit operator action required to allow a revoked identity to enroll again. */
    @Synchronized
    fun resetRevoked(stableId: String, nowMs: Long): OfflineMeshTrustRecord {
        val snapshot = readSnapshot()
        val existing = snapshot.records.singleOrNull { it.stableId == stableId }
            ?: throw PolicyException("UNKNOWN_IDENTITY")
        if (existing.state != OfflineMeshTrustState.REVOKED) return existing
        val reset = existing.copy(
            state = OfflineMeshTrustState.PENDING_APPROVAL,
            approvedAtMs = null,
            revokedAtMs = nowMs,
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = replace(snapshot.records, reset)))
        return reset
    }

    @Synchronized
    fun proposeRotation(
        stableId: String,
        newPublicKeyX509: ByteArray,
        proof: ByteArray,
        nowMs: Long
    ): OfflineMeshTrustRecord {
        val newKey = validatePublicKey(newPublicKeyX509)
        val snapshot = readSnapshot()
        val existing = snapshot.records.singleOrNull { it.stableId == stableId }
            ?: throw PolicyException("UNKNOWN_IDENTITY")
        if (existing.state != OfflineMeshTrustState.TRUSTED) {
            throw PolicyException("ROTATION_REQUIRES_TRUSTED_IDENTITY")
        }
        if (!verifyRotation(existing.publicKeyX509, newKey, proof)) {
            throw PolicyException("INVALID_ROTATION_PROOF")
        }
        val pending = existing.copy(
            pendingRotationKeyX509 = newKey,
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = replace(snapshot.records, pending)))
        return pending
    }

    @Synchronized
    fun acceptRotation(stableId: String, newPublicKeyX509: ByteArray, nowMs: Long): OfflineMeshTrustRecord {
        val newKey = validatePublicKey(newPublicKeyX509)
        val snapshot = readSnapshot()
        val existing = snapshot.records.singleOrNull { it.stableId == stableId }
            ?: throw PolicyException("UNKNOWN_IDENTITY")
        if (existing.state != OfflineMeshTrustState.TRUSTED ||
            existing.pendingRotationKeyX509?.contentEquals(newKey) != true
        ) {
            throw PolicyException("ROTATION_NOT_PENDING")
        }
        val rotated = existing.copy(
            publicKeyX509 = newKey,
            pendingRotationKeyX509 = null,
            retiredKeyFingerprints = existing.retiredKeyFingerprints + fingerprint(existing.publicKeyX509),
            version = snapshot.revision + 1
        )
        write(snapshot.copy(revision = snapshot.revision + 1, records = replace(snapshot.records, rotated)))
        return rotated
    }

    @Synchronized
    fun isTrusted(publicKeyX509: ByteArray): Boolean = runCatching {
        val key = validatePublicKey(publicKeyX509)
        val snapshot = readSnapshotOrNull() ?: return@runCatching false
        val record = find(snapshot.records, fingerprint(key)) ?: return@runCatching false
        record.state == OfflineMeshTrustState.TRUSTED &&
            MessageDigest.isEqual(record.publicKeyX509, key)
    }.getOrDefault(false)

    @Synchronized
    fun trustedPublicKeysX509(): List<ByteArray> = readSnapshotOrNull()?.records
        ?.filter { it.state == OfflineMeshTrustState.TRUSTED }
        ?.map(OfflineMeshTrustRecord::copyPublicKey)
        .orEmpty()

    @Synchronized
    fun records(): List<OfflineMeshTrustRecord> = readSnapshotOrNull()?.records.orEmpty()

    @Synchronized
    fun require(publicKeyX509: ByteArray): OfflineMeshTrustRecord =
        observe(publicKeyX509, 0L)

    private fun readSnapshot(): Snapshot = readSnapshotOrNull()
        ?: throw StorageException("OFFLINE_MESH_TRUST_STORAGE_INVALID")

    private fun readSnapshotOrNull(): Snapshot? {
        val entry = try {
            backend.read()
        } catch (_: Throwable) {
            return null
        } ?: return Snapshot(0L, emptyList())
        return runCatching {
            val bytes = entry.payload.toByteArray(StandardCharsets.UTF_8)
            require(MessageDigest.isEqual(macProvider.mac(bytes), entry.mac))
            OfflineMeshTrustCodec.decode(entry.payload)
        }.getOrNull()
    }

    private fun write(snapshot: Snapshot) {
        val payload = OfflineMeshTrustCodec.encode(snapshot)
        val ok = try {
            backend.write(payload, macProvider.mac(payload.toByteArray(StandardCharsets.UTF_8)))
        } catch (error: Throwable) {
            throw StorageException("OFFLINE_MESH_TRUST_STORAGE_WRITE_FAILED", error)
        }
        if (!ok) throw StorageException("OFFLINE_MESH_TRUST_STORAGE_WRITE_FAILED")
    }

    private fun find(records: List<OfflineMeshTrustRecord>, fingerprint: String): OfflineMeshTrustRecord? =
        records.firstOrNull { record ->
            fingerprint(record.publicKeyX509) == fingerprint || fingerprint in record.retiredKeyFingerprints
        }

    private fun replace(
        records: List<OfflineMeshTrustRecord>,
        replacement: OfflineMeshTrustRecord
    ): List<OfflineMeshTrustRecord> = records.map { if (it.stableId == replacement.stableId) replacement else it }

    private fun errorRecord(key: ByteArray, nowMs: Long) = OfflineMeshTrustRecord(
        stableId = fingerprint(key),
        publicKeyX509 = key,
        state = OfflineMeshTrustState.ERROR,
        approvedAtMs = null,
        revokedAtMs = nowMs,
        version = 0L
    )

    private fun validatePublicKey(encoded: ByteArray): ByteArray {
        require(encoded.isNotEmpty()) { "EMPTY_PUBLIC_KEY" }
        val key = runCatching {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        }.getOrElse { throw PolicyException("INVALID_PUBLIC_KEY") }
        require(key is java.security.interfaces.ECPublicKey) { "PUBLIC_KEY_NOT_EC" }
        runCatching { OfflineMeshGattCryptoCodec.rawPoint(key) }
            .getOrElse { throw PolicyException("PUBLIC_KEY_NOT_P256") }
        return encoded.copyOf()
    }

    private fun fingerprint(encoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun verifyRotation(oldKey: ByteArray, newKey: ByteArray, proof: ByteArray): Boolean = runCatching {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(oldKey))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(rotationMessage(newKey))
            verify(proof)
        }
    }.getOrDefault(false)

    data class Snapshot(val revision: Long, val records: List<OfflineMeshTrustRecord>)

    companion object {
        fun fingerprint(publicKeyX509: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(publicKeyX509)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun rotationMessage(newPublicKeyX509: ByteArray): ByteArray =
            "sentinel-offline-mesh-key-rotation-v1".toByteArray(StandardCharsets.US_ASCII) + newPublicKeyX509
    }
}

private object OfflineMeshTrustCodec {
    private const val VERSION = "1"

    fun encode(snapshot: OfflineMeshTrustStore.Snapshot): String = buildString {
        append(VERSION).append('|').append(snapshot.revision).append('\n')
        snapshot.records.sortedBy { it.stableId }.forEach { record ->
            append(b64(record.stableId)).append('|')
                .append(b64(record.publicKeyX509)).append('|')
                .append(record.state.name).append('|')
                .append(record.approvedAtMs ?: -1L).append('|')
                .append(record.revokedAtMs ?: -1L).append('|')
                .append(record.version).append('|')
                .append(record.retiredKeyFingerprints.sorted().joinToString(",")).append('|')
                .append(record.pendingRotationKeyX509?.let(::b64).orEmpty()).append('\n')
        }
    }

    fun decode(payload: String): OfflineMeshTrustStore.Snapshot {
        val lines = payload.split('\n')
        require(lines.isNotEmpty() && lines.first().isNotEmpty())
        val header = lines.first().split('|')
        require(header.size == 2 && header[0] == VERSION)
        val records = lines.drop(1).filter(String::isNotEmpty).map { line ->
            val fields = line.split('|')
            require(fields.size == 8)
            OfflineMeshTrustRecord(
                stableId = unb64(fields[0]),
                publicKeyX509 = unb64Bytes(fields[1]),
                state = OfflineMeshTrustState.valueOf(fields[2]),
                approvedAtMs = fields[3].toLong().takeUnless { it < 0L },
                revokedAtMs = fields[4].toLong().takeUnless { it < 0L },
                version = fields[5].toLong(),
                retiredKeyFingerprints = fields[6].takeIf(String::isNotEmpty)?.split(',')?.toSet().orEmpty(),
                pendingRotationKeyX509 = fields[7].takeIf(String::isNotEmpty)?.let(::unb64Bytes)
            )
        }
        require(records.map(OfflineMeshTrustRecord::stableId).distinct().size == records.size)
        return OfflineMeshTrustStore.Snapshot(header[1].toLong(), records)
    }

    private fun b64(value: String): String = b64(value.toByteArray(StandardCharsets.UTF_8))
    private fun b64(value: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun unb64(value: String): String = String(unb64Bytes(value), StandardCharsets.UTF_8)
    private fun unb64Bytes(value: String): ByteArray = java.util.Base64.getUrlDecoder().decode(value)
}

/** Device-local backend stored outside Android backup and protected by an AndroidKeyStore MAC key. */
class OfflineMeshGattAndroidTrustStore(context: Context) {
    private val delegate = OfflineMeshTrustStore(
        FileBackend(File(context.applicationContext.noBackupFilesDir, FILE_NAME)),
        AndroidKeyStoreMac()
    )

    fun observe(publicKeyX509: ByteArray, nowMs: Long) = delegate.observe(publicKeyX509, nowMs)
    fun requestApproval(publicKeyX509: ByteArray, nowMs: Long) = delegate.requestApproval(publicKeyX509, nowMs)
    fun approve(stableId: String, nowMs: Long) = delegate.approve(stableId, nowMs)
    fun revoke(stableId: String, nowMs: Long) = delegate.revoke(stableId, nowMs)
    fun resetRevoked(stableId: String, nowMs: Long) = delegate.resetRevoked(stableId, nowMs)
    fun proposeRotation(stableId: String, newPublicKeyX509: ByteArray, proof: ByteArray, nowMs: Long) =
        delegate.proposeRotation(stableId, newPublicKeyX509, proof, nowMs)
    fun acceptRotation(stableId: String, newPublicKeyX509: ByteArray, nowMs: Long) =
        delegate.acceptRotation(stableId, newPublicKeyX509, nowMs)
    fun isTrusted(publicKeyX509: ByteArray) = delegate.isTrusted(publicKeyX509)
    fun trustedPublicKeysX509() = delegate.trustedPublicKeysX509()
    fun records() = delegate.records()

    private companion object {
        const val FILE_NAME = "offline_mesh_trust_v1.bin"
    }
}

private class FileBackend(private val file: File) : OfflineMeshTrustBackend {
    override fun read(): OfflineMeshTrustBackend.Entry? {
        if (!file.exists()) return null
        val lines = file.readText(StandardCharsets.UTF_8).split('\n')
        require(lines.size == 2 && lines[0].isNotEmpty() && lines[1].isNotEmpty())
        return OfflineMeshTrustBackend.Entry(
            String(java.util.Base64.getDecoder().decode(lines[0]), StandardCharsets.UTF_8),
            java.util.Base64.getDecoder().decode(lines[1])
        )
    }

    override fun write(payload: String, mac: ByteArray): Boolean {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(
            java.util.Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8)) +
                '\n' + java.util.Base64.getEncoder().encodeToString(mac),
            StandardCharsets.UTF_8
        )
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return false
        }
        return true
    }
}

private class AndroidKeyStoreMac : OfflineMeshTrustMac {
    override fun mac(payload: ByteArray): ByteArray = Mac.getInstance(HMAC).run {
        init(loadOrCreateKey())
        doFinal(payload)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val HMAC = "HmacSHA256"
        const val ALIAS = "sentinel_offline_mesh_trust_hmac_v1"
    }
}
