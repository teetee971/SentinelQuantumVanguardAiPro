package com.sentinel.quantum.security

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECFieldFp
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Public identity and trust boundary used by the offline GATT handshake. */
class OfflineMeshGattCryptoIdentity private constructor(
    publicKeyX509: ByteArray,
    private val signer: (ByteArray) -> ByteArray,
    private val peerTrust: (ByteArray) -> Boolean
) {
    val publicKeyX509: ByteArray = publicKeyX509.copyOf()
    private val publicKey: ECPublicKey =
        OfflineMeshGattCryptoCodec.decodeEcPublicKeyX509(this.publicKeyX509) as ECPublicKey
    internal val publicKeyWire: ByteArray = OfflineMeshGattCryptoCodec.rawPoint(
        publicKey
    )

    init {
        require(this.publicKeyX509.isNotEmpty())
        val bindingChallenge = "sentinel-offline-mesh-identity-v1".toByteArray(StandardCharsets.US_ASCII)
        require(
            OfflineMeshGattCryptoCodec.verifyEcdsa(
                publicKey,
                bindingChallenge,
                signer(bindingChallenge).copyOf()
            )
        ) { "identity signer does not match advertised public key" }
    }

    internal fun sign(transcript: ByteArray): ByteArray =
        OfflineMeshGattCryptoCodec.derSignatureToRaw(signer(transcript).copyOf())

    internal fun trusts(peerPublicKeyX509: ByteArray): Boolean =
        runCatching { peerTrust(peerPublicKeyX509.copyOf()) }.getOrDefault(false)

    companion object {
        /** Builds an identity from an exportable test key or another JCA key provider. */
        fun fromKeyPair(
            keyPair: KeyPair,
            trusts: (ByteArray) -> Boolean
        ): OfflineMeshGattCryptoIdentity {
            val privateKey = keyPair.private
            require(privateKey.algorithm.equals("EC", ignoreCase = true))
            return fromSigner(keyPair.public.encoded, { bytes -> sign(privateKey, bytes) }, trusts)
        }

        /**
         * Builds an identity around an AndroidKeyStore signer. The private key is
         * never exported; the caller supplies the public X.509 key and signs the
         * transcript through the keystore-backed callback.
         */
        fun fromSigner(
            publicKeyX509: ByteArray,
            signer: (ByteArray) -> ByteArray,
            trusts: (ByteArray) -> Boolean
        ): OfflineMeshGattCryptoIdentity = OfflineMeshGattCryptoIdentity(
            publicKeyX509.copyOf(), signer, trusts
        )

        private fun sign(privateKey: PrivateKey, bytes: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(bytes)
                sign()
            }
    }
}

/**
 * Mutual authenticated key exchange and application encryption for one GATT
 * link. The class intentionally has no default identity or trust store.
 */
class OfflineMeshGattCryptoSession(
    val role: Role,
    private val identity: OfflineMeshGattCryptoIdentity?,
    private val random: SecureRandom = SecureRandom()
) {
    enum class Role { INITIATOR, RESPONDER }
    enum class State { NEW, WAITING_RESPONSE, WAITING_CONFIRMATION, ESTABLISHED, FAILED }

    data class HandshakeResult(val accepted: Boolean, val outbound: ByteArray? = null)

    private var currentState = State.NEW
    private var ephemeral: KeyPair? = null
    private var localNonce: ByteArray? = null
    private var initUnsigned: ByteArray? = null
    private var transcript: ByteArray? = null
    private var sendKey: ByteArray? = null
    private var receiveKey: ByteArray? = null
    private var nextSendCounter = 1L
    private var peerPublicX509: ByteArray? = null
    private val receivedCounters = HashSet<Long>()

    @Synchronized fun state(): State = currentState
    @Synchronized fun isEstablished(): Boolean = currentState == State.ESTABLISHED
    @Synchronized fun peerPublicIdentityX509(): ByteArray? = peerPublicX509?.copyOf()

    @Synchronized fun start(): ByteArray? {
        if (role != Role.INITIATOR || currentState != State.NEW || identity == null) return null
        val keyPair = generateEphemeral()
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val unsigned = OfflineMeshGattCryptoCodec.helloUnsigned(
            identity.publicKeyWire, OfflineMeshGattCryptoCodec.rawPoint(keyPair.public), nonce
        )
        val signature = identity.sign(signatureInput(HELLO, unsigned))
        ephemeral = keyPair
        localNonce = nonce
        initUnsigned = unsigned
        currentState = State.WAITING_RESPONSE
        return encodeControl(HELLO, unsigned + sized(signature))
    }

    @Synchronized fun receive(frameBytes: ByteArray): HandshakeResult {
        if (identity == null || currentState == State.FAILED || currentState == State.ESTABLISHED) {
            return HandshakeResult(false)
        }
        return runCatching {
            val frame = OfflineMeshGattFrameCodec.decode(frameBytes) as?
                OfflineMeshGattFrameCodec.Frame.Control ?: return@runCatching fail()
            when {
                role == Role.RESPONDER && currentState == State.NEW && frame.code == HELLO ->
                    receiveHello(frame.payload)
                role == Role.INITIATOR && currentState == State.WAITING_RESPONSE && frame.code == RESPONSE ->
                    receiveResponse(frame.payload)
                role == Role.RESPONDER && currentState == State.WAITING_CONFIRMATION && frame.code == FINISH ->
                    receiveFinish(frame.payload)
                else -> fail()
            }
        }.getOrElse { fail() }
    }

    @Synchronized fun encrypt(plaintext: ByteArray, counter: Long): ByteArray {
        check(isEstablished()) { "GATT crypto session is not authenticated" }
        require(
            counter == nextSendCounter &&
                counter < Long.MAX_VALUE &&
                plaintext.isNotEmpty() &&
                plaintext.size <= MAX_PLAINTEXT
        ) { "AES-GCM send counter must be strictly monotonic" }
        val key = requireNotNull(sendKey)
        val nonce = nonce(counter, direction = true)
        val aad = aad(counter, direction = true)
        val cipherText = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
            doFinal(plaintext.copyOf())
        }
        nextSendCounter = counter + 1L
        return ByteBuffer.allocate(ENVELOPE_HEADER + cipherText.size).order(ByteOrder.BIG_ENDIAN)
            .put(ENVELOPE_VERSION.toByte()).putLong(counter).put(cipherText).array()
    }

    @Synchronized fun decrypt(envelope: ByteArray): ByteArray? {
        if (!isEstablished() || envelope.size < ENVELOPE_HEADER + TAG_BYTES) return null
        val input = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN)
        if ((input.get().toInt() and 0xff) != ENVELOPE_VERSION) return null
        val counter = input.long
        if (counter <= 0L || receivedCounters.contains(counter)) return null
        val cipherText = ByteArray(input.remaining()).also(input::get)
        if (receivedCounters.size >= MAX_RECEIVED_COUNTERS) return null
        return runCatching {
            val key = requireNotNull(receiveKey)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(counter, false)))
                updateAAD(aad(counter, false))
                doFinal(cipherText)
            }
        }.getOrNull()?.also { receivedCounters.add(counter) }
    }

    @Synchronized fun sessionProof(): ByteArray = requireNotNull(transcript).copyOf()

    @Synchronized internal fun reset() {
        sendKey?.fill(0)
        receiveKey?.fill(0)
        transcript?.fill(0)
        peerPublicX509?.fill(0)
        localNonce?.fill(0)
        initUnsigned?.fill(0)
        currentState = State.NEW
        ephemeral = null
        localNonce = null
        initUnsigned = null
        transcript = null
        sendKey = null
        receiveKey = null
        nextSendCounter = 1L
        peerPublicX509 = null
        receivedCounters.clear()
    }

    private fun receiveHello(payload: ByteArray): HandshakeResult {
        val parsed = OfflineMeshGattCryptoCodec.parseHello(payload) ?: return fail()
        val peerPublic = runCatching { OfflineMeshGattCryptoCodec.decodeEcPublicKeyRaw(parsed.identityWire) }
            .getOrNull() ?: return fail()
        val peerX509 = peerPublic.encoded
        if (!identity!!.trusts(peerX509) ||
            !verify(peerPublic, signatureInput(HELLO, parsed.unsigned), parsed.signature)
        ) return fail()
        peerPublicX509 = peerX509.copyOf()

        val keyPair = generateEphemeral()
        val responderNonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val unsignedResponse = OfflineMeshGattCryptoCodec.responseUnsigned(
            parsed.nonce, responderNonce, identity.publicKeyWire,
            OfflineMeshGattCryptoCodec.rawPoint(keyPair.public)
        )
        val signature = identity.sign(signatureInput(RESPONSE, parsed.unsigned + unsignedResponse))
        val shared = sharedSecret(keyPair, parsed.ephemeralWire)
        installKeys(shared, parsed.unsigned + unsignedResponse)
        ephemeral = keyPair
        localNonce = responderNonce
        initUnsigned = parsed.unsigned
        currentState = State.WAITING_CONFIRMATION
        return HandshakeResult(true, encodeControl(RESPONSE, unsignedResponse + sized(signature)))
    }

    private fun receiveResponse(payload: ByteArray): HandshakeResult {
        val parsed = OfflineMeshGattCryptoCodec.parseResponse(payload) ?: return fail()
        val init = initUnsigned ?: return fail()
        val nonce = localNonce ?: return fail()
        if (!nonce.contentEquals(parsed.initiatorNonce)) return fail()
        val peerPublic = runCatching { OfflineMeshGattCryptoCodec.decodeEcPublicKeyRaw(parsed.identityWire) }
            .getOrNull() ?: return fail()
        if (!identity!!.trusts(peerPublic.encoded) ||
            !verify(peerPublic, signatureInput(RESPONSE, init + parsed.unsigned), parsed.signature)
        ) return fail()
        peerPublicX509 = peerPublic.encoded.copyOf()
        val keyPair = ephemeral ?: return fail()
        installKeys(sharedSecret(keyPair, parsed.ephemeralWire), init + parsed.unsigned)
        currentState = State.ESTABLISHED
        val proof = finishMac()
        return HandshakeResult(true, encodeControl(FINISH, proof))
    }

    private fun receiveFinish(payload: ByteArray): HandshakeResult {
        if (payload.size != FINISH_BYTES || !MessageDigest.isEqual(payload, finishMac())) return fail()
        currentState = State.ESTABLISHED
        return HandshakeResult(true)
    }

    private fun installKeys(shared: ByteArray, transcriptInput: ByteArray) {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            DOMAIN + byteArrayOf(RESPONSE.toByte()) + transcriptInput
        )
        val prk = hkdfExtract(digest, shared)
        val initiatorKey = hkdfExpand(prk, INFO_I2R, KEY_BYTES)
        val responderKey = hkdfExpand(prk, INFO_R2I, KEY_BYTES)
        transcript = digest
        if (role == Role.INITIATOR) {
            sendKey = initiatorKey
            receiveKey = responderKey
        } else {
            sendKey = responderKey
            receiveKey = initiatorKey
        }
    }

    private fun finishMac(): ByteArray = hmac(
        requireNotNull(if (role == Role.INITIATOR) sendKey else receiveKey),
        DOMAIN + FINISH_LABEL + requireNotNull(transcript)
    )

    private fun nonce(counter: Long, direction: Boolean): ByteArray = ByteBuffer.allocate(12)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(if (direction == (role == Role.INITIATOR)) NONCE_I2R else NONCE_R2I)
        .putLong(counter).array()

    private fun aad(counter: Long, direction: Boolean): ByteArray =
        DOMAIN + byteArrayOf(if (direction == (role == Role.INITIATOR)) 1 else 2) + ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN).putLong(counter).array() + requireNotNull(transcript)

    private fun fail(): HandshakeResult {
        currentState = State.FAILED
        return HandshakeResult(false)
    }

    private fun generateEphemeral(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(CURVE))
    }.generateKeyPair()

    private fun sharedSecret(local: KeyPair, peerWire: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(local.private)
        agreement.doPhase(OfflineMeshGattCryptoCodec.decodeEcPublicKeyRaw(peerWire), true)
        return agreement.generateSecret()
    }

    private fun verify(publicKey: PublicKey, bytes: ByteArray, rawSignature: ByteArray): Boolean =
        runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(bytes)
                verify(OfflineMeshGattCryptoCodec.rawSignatureToDer(rawSignature))
            }
        }.getOrDefault(false)

    private fun signatureInput(type: Int, bytes: ByteArray): ByteArray =
        DOMAIN + byteArrayOf(type.toByte()) + bytes

    private fun encodeControl(code: Int, payload: ByteArray): ByteArray =
        OfflineMeshGattFrameCodec.encode(OfflineMeshGattFrameCodec.Frame.Control(code, payload))

    private fun sized(bytes: ByteArray): ByteArray = ByteBuffer.allocate(2 + bytes.size)
        .order(ByteOrder.BIG_ENDIAN).putShort(bytes.size.toShort()).put(bytes).array()

    companion object {
        private const val HELLO = 1
        private const val RESPONSE = 2
        private const val FINISH = 3
        private const val CURVE = "secp256r1"
        private const val NONCE_BYTES = 16
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val ENVELOPE_VERSION = 1
        private const val ENVELOPE_HEADER = 1 + 8
        private const val MAX_PLAINTEXT = 64 * 1024 - ENVELOPE_HEADER - TAG_BYTES
        private const val MAX_RECEIVED_COUNTERS = 8_192
        private const val KEY_BYTES = 32
        private const val FINISH_BYTES = 32
        private const val NONCE_I2R = 0x494e4954
        private const val NONCE_R2I = 0x52455350
        private val DOMAIN = "sentinel-offline-mesh-gatt-v1".toByteArray(StandardCharsets.US_ASCII)
        private val FINISH_LABEL = "-finish".toByteArray(StandardCharsets.US_ASCII)
        private val INFO_I2R = "initiator-to-responder".toByteArray(StandardCharsets.US_ASCII)
        private val INFO_R2I = "responder-to-initiator".toByteArray(StandardCharsets.US_ASCII)

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

        private fun hkdfExtract(salt: ByteArray, input: ByteArray): ByteArray = hmac(salt, input)

        private fun hkdfExpand(prk: ByteArray, info: ByteArray, size: Int): ByteArray {
            var previous = ByteArray(0)
            val output = ByteArray(size)
            var offset = 0
            var counter = 1
            while (offset < size) {
                previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
                val copied = minOf(previous.size, size - offset)
                previous.copyInto(output, offset, 0, copied)
                offset += copied
                counter++
            }
            return output
        }
    }
}

internal object OfflineMeshGattCryptoCodec {
    private const val RAW_POINT_BYTES = 65
    private const val NONCE_BYTES = 16
    private const val MAX_SIGNATURE_BYTES = 80

    data class Hello(val unsigned: ByteArray, val identityWire: ByteArray, val ephemeralWire: ByteArray, val nonce: ByteArray, val signature: ByteArray)
    data class Response(val unsigned: ByteArray, val identityWire: ByteArray, val ephemeralWire: ByteArray, val initiatorNonce: ByteArray, val signature: ByteArray)

    fun helloUnsigned(identityWire: ByteArray, ephemeralWire: ByteArray, nonce: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + 2 + identityWire.size + 2 + ephemeralWire.size + NONCE_BYTES)
            .order(ByteOrder.BIG_ENDIAN).put(1.toByte()).putSized(identityWire).putSized(ephemeralWire).put(nonce).array()

    fun responseUnsigned(
        initiatorNonce: ByteArray,
        responderNonce: ByteArray,
        identityWire: ByteArray,
        ephemeralWire: ByteArray
    ): ByteArray = ByteBuffer.allocate(
        1 + NONCE_BYTES + NONCE_BYTES + 2 + identityWire.size + 2 + ephemeralWire.size
    ).order(ByteOrder.BIG_ENDIAN).put(1.toByte()).put(initiatorNonce).put(responderNonce)
        .putSized(identityWire).putSized(ephemeralWire).array()

    fun parseHello(payload: ByteArray): Hello? = runCatching {
        val input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        if ((input.get().toInt() and 0xff) != 1) return null
        val identity = input.readSized(RAW_POINT_BYTES) ?: return null
        val ephemeral = input.readSized(RAW_POINT_BYTES) ?: return null
        if (input.remaining() < NONCE_BYTES + 2) return null
        val nonce = ByteArray(NONCE_BYTES).also(input::get)
        val unsignedLength = input.position()
        val signature = input.readSized(MAX_SIGNATURE_BYTES) ?: return null
        if (input.hasRemaining()) return null
        val unsigned = payload.copyOfRange(0, unsignedLength)
        Hello(unsigned, identity, ephemeral, nonce, signature)
    }.getOrNull()

    fun parseResponse(payload: ByteArray): Response? = runCatching {
        val input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        if ((input.get().toInt() and 0xff) != 1 || input.remaining() < NONCE_BYTES * 2 + 2) return null
        val initiatorNonce = ByteArray(NONCE_BYTES).also(input::get)
        val responderNonce = ByteArray(NONCE_BYTES).also(input::get)
        val identity = input.readSized(RAW_POINT_BYTES) ?: return null
        val ephemeral = input.readSized(RAW_POINT_BYTES) ?: return null
        val unsignedLength = input.position()
        val signature = input.readSized(MAX_SIGNATURE_BYTES) ?: return null
        if (input.hasRemaining()) return null
        Response(payload.copyOfRange(0, unsignedLength), identity, ephemeral, initiatorNonce, signature)
    }.getOrNull()

    fun decodeEcPublicKeyRaw(raw: ByteArray): PublicKey {
        require(raw.size == RAW_POINT_BYTES && raw[0].toInt() == 4)
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    fun decodeEcPublicKeyX509(x509: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(x509.copyOf())).also {
            require(it is ECPublicKey && isP256(it.params)) { "only P-256 identities are supported" }
        }

    fun rawPoint(publicKey: PublicKey): ByteArray {
        val ec = publicKey as? ECPublicKey ?: error("GATT identity must use EC")
        require(isP256(ec.params)) { "only P-256 identities are supported" }
        return byteArrayOf(4) + fixed(ec.w.affineX) + fixed(ec.w.affineY)
    }

    fun verifyEcdsa(publicKey: PublicKey, bytes: ByteArray, derSignature: ByteArray): Boolean =
        runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(bytes)
                verify(derSignature)
            }
        }.getOrDefault(false)

    fun derSignatureToRaw(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte() &&
            (der[1].toInt() and 0xff) == der.size - 2)
        var index = 2
        val r = readDerInteger(der, index).also { index = it.second }.first
        val s = readDerInteger(der, index).also { index = it.second }.first
        require(index == der.size)
        return fixed(BigInteger(r)) + fixed(BigInteger(s))
    }

    fun rawSignatureToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64)
        val r = derInteger(raw.copyOfRange(0, 32))
        val s = derInteger(raw.copyOfRange(32, 64))
        val body = r + s
        require(body.size < 128)
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun derInteger(raw: ByteArray): ByteArray {
        val first = raw.indexOfFirst { it != 0.toByte() }
        val value = if (first < 0) byteArrayOf(0) else raw.copyOfRange(first, raw.size)
        val normalized = if (value[0].toInt() and 0x80 != 0) byteArrayOf(0) + value else value
        return byteArrayOf(0x02, normalized.size.toByte()) + normalized
    }

    private fun readDerInteger(bytes: ByteArray, start: Int): Pair<ByteArray, Int> {
        require(start + 2 <= bytes.size && bytes[start] == 0x02.toByte())
        val length = bytes[start + 1].toInt() and 0xff
        val end = start + 2 + length
        require(length in 1..33 && end <= bytes.size)
        return bytes.copyOfRange(start + 2, end) to end
    }

    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val unsigned = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        require(unsigned.size <= 32)
        return ByteArray(32 - unsigned.size) + unsigned
    }

    private fun ByteBuffer.putSized(bytes: ByteArray): ByteBuffer {
        require(bytes.size in 1..0xffff)
        return putShort(bytes.size.toShort()).put(bytes)
    }

    private fun ByteBuffer.readSized(maximum: Int): ByteArray? {
        if (remaining() < 2) return null
        val length = short.toInt() and 0xffff
        if (length !in 1..maximum || remaining() < length) return null
        return ByteArray(length).also(::get)
    }

    private fun isP256(params: ECParameterSpec): Boolean {
        val field = params.curve.field as? ECFieldFp ?: return false
        val expected = p256Spec()
        val expectedField = expected.curve.field as? ECFieldFp ?: return false
        val actualGenerator = params.generator
        val expectedGenerator = expected.generator
        return field.p == expectedField.p &&
            params.curve.a == expected.curve.a &&
            params.curve.b == expected.curve.b &&
            actualGenerator.affineX == expectedGenerator.affineX &&
            actualGenerator.affineY == expectedGenerator.affineY &&
            params.order == expected.order &&
            params.cofactor == expected.cofactor
    }

    private fun p256Spec(): ECParameterSpec = AlgorithmParameters.getInstance("EC").apply {
        init(ECGenParameterSpec("secp256r1"))
    }.getParameterSpec(ECParameterSpec::class.java)
}
