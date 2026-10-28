package com.sentinel.quantum.security

import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

// Deliberately simulated provider. AES/GCM itself uses the real JVM provider.
class HostProvider : Provider("AndroidKeyStore", "1.0", "HOST TEST DOUBLE ONLY") {
    init {
        put("KeyStore.AndroidKeyStore", HostKeyStore::class.java.name)
        put("KeyGenerator.AES", HostKeyGenerator::class.java.name)
    }
}
class HostKeyStore : KeyStoreSpi() {
    companion object {
        var key: SecretKey? = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        var onRead: (() -> Unit)? = null
        var generations = 0
    }
    override fun engineGetKey(alias: String?, password: CharArray?): Key? {
        onRead?.invoke()
        return key
    }
    override fun engineLoad(stream: InputStream?, password: CharArray?) {}
    override fun engineStore(stream: OutputStream?, password: CharArray?) {}
    override fun engineContainsAlias(alias: String?) = key != null
    override fun engineSize() = if (key == null) 0 else 1
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(listOf("sentinel_recording_vault_r1"))
    override fun engineDeleteEntry(alias: String?) { key = null }
    override fun engineGetCreationDate(alias: String?) = Date(0)
    override fun engineIsKeyEntry(alias: String?) = key != null
    override fun engineIsCertificateEntry(alias: String?) = false
    override fun engineGetCertificate(alias: String?): Certificate? = null
    override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
    override fun engineGetCertificateAlias(cert: Certificate?): String? = null
    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = error("unused")
    override fun engineSetKeyEntry(alias: String?, bytes: ByteArray?, chain: Array<Certificate>?) = error("unused")
    override fun engineSetKeyEntry(alias: String?, value: Key?, password: CharArray?, chain: Array<Certificate>?) = error("unused")
}
class HostKeyGenerator : KeyGeneratorSpi() {
    override fun engineInit(random: SecureRandom?) {}
    override fun engineInit(size: Int, random: SecureRandom?) {}
    override fun engineInit(spec: AlgorithmParameterSpec?, random: SecureRandom?) {}
    override fun engineGenerateKey(): SecretKey {
        HostKeyStore.generations++
        return SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES").also { HostKeyStore.key = it }
    }
}
