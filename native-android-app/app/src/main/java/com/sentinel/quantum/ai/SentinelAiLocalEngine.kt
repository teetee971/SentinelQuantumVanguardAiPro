package com.sentinel.quantum.ai

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Local recommendation provider only. Owns no approval port, action adapter or Android permission.
 * Call infer off the main thread. The trusted UI must obtain consent before supplying text.
 * Not yet connected to an Android composition root; HTTP policy is deliberately unchanged.
 */
class SentinelAiLocalEngine internal constructor(
    pairingKey: ByteArray,
    private val transport: Transport
) : AutoCloseable {
    constructor(pairingKey: ByteArray) : this(pairingKey, LoopbackTransport())

    data class Recommendation(val reply: String, val action: String)
    internal data class Packet(val status: Int, val body: String, val signature: String?)
    internal interface Transport : AutoCloseable {
        fun exchange(path: String, body: String?, headers: Map<String, String>): Packet
    }
    companion object {
        const val MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        private val HEX = Regex("[0-9a-f]{64}")
    }
    private val secret = pairingKey.copyOf().also { require(it.size == 32) }
    private val keyLock = Any()
    private val closed = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)

    private fun ensureOpen() {
        if (closed.get()) throw IOException("Local engine closed")
    }
    private fun signature(key: ByteArray, text: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun verify(expected: String, actual: String?) {
        if (actual == null || !HEX.matches(actual) || !MessageDigest.isEqual(
                expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))) {
            throw IOException("Local engine authentication failed")
        }
    }
    private fun exchange(path: String, body: String? = null, headers: Map<String, String> = emptyMap()): Packet {
        ensureOpen()
        val result = transport.exchange(path, body, headers)
        ensureOpen()
        if (result.status != 200 || result.body.toByteArray(Charsets.UTF_8).size > 8192) {
            throw IOException("Local engine unavailable or response rejected")
        }
        return result
    }
    fun infer(message: String): Recommendation {
        require(message.isNotEmpty() && message.length <= 512 && '\u0000' !in message)
        ensureOpen()
        if (!busy.compareAndSet(false, true)) throw IOException("Local engine busy")
        var requestKey: ByteArray? = null
        try {
            requestKey = synchronized(keyLock) { ensureOpen(); secret.copyOf() }
            val client = ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            val challenge = JSONObject(exchange("/challenge?client_nonce=$client").body)
            if (challenge.keys().asSequence().toSet() != setOf("nonce", "model_sha256", "proof")) {
                throw IOException("Local challenge rejected")
            }
            val nonce = challenge.getString("nonce")
            val model = challenge.getString("model_sha256")
            if (!HEX.matches(nonce) || model != MODEL_SHA256) throw IOException("Local model not approved")
            verify(signature(requestKey, "server\u0000$client\u0000$nonce\u0000$model"), challenge.getString("proof"))
            val body = JSONObject().put("message", message).toString()
            val packet = exchange("/infer", body, mapOf(
                "X-Sentinel-Nonce" to nonce,
                "X-Sentinel-Mac" to signature(requestKey, "client\u0000$nonce\u0000$body")
            ))
            verify(signature(requestKey, "response\u0000$nonce\u0000${packet.body}"), packet.signature)
            val output = JSONObject(packet.body)
            if (output.keys().asSequence().toSet() != setOf("reply", "action") ||
                output.get("reply") !is String || output.get("action") !is String) {
                throw IOException("Local recommendation rejected")
            }
            val reply = output.getString("reply")
            val action = output.getString("action")
            if (reply.isEmpty() || reply.length > 256 || '\u0000' in reply ||
                action !in setOf("NONE", "CREATE_AUDIT_CHECKPOINT")) {
                throw IOException("Local recommendation rejected")
            }
            ensureOpen()
            return Recommendation(reply, action)
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            // Never include body, prompt, pairing key or provider exception in diagnostics.
            throw IOException("Local protocol rejected")
        } finally {
            requestKey?.fill(0)
            busy.set(false)
        }
    }
    override fun close() {
        closed.set(true)
        synchronized(keyLock) { secret.fill(0) }
        transport.close()
    }

    private class LoopbackTransport : Transport {
        private val closed = AtomicBoolean(false)
        private val active = AtomicReference<HttpURLConnection?>(null)
        override fun exchange(path: String, body: String?, headers: Map<String, String>): Packet {
            if (closed.get()) throw IOException("Local transport closed")
            // Fixed loopback origin, no system proxy or redirects, no caller-supplied URL.
            val connection = URL("http://127.0.0.1:18080$path")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
            if (!active.compareAndSet(null, connection)) {
                connection.disconnect()
                throw IOException("Local transport busy")
            }
            try {
                if (closed.get()) throw IOException("Local transport closed")
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 2000
                connection.readTimeout = 35000
                connection.useCaches = false
                connection.setRequestProperty("Connection", "close")
                for ((name, value) in headers) connection.setRequestProperty(name, value)
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                if (status != 200) return Packet(status, "", null)
                val bytes = connection.inputStream.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (out.size() + count > 8192) throw IOException("Local response too large")
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                if (closed.get()) throw IOException("Local transport closed")
                return Packet(status, String(bytes, Charsets.UTF_8), connection.getHeaderField("X-Sentinel-Mac"))
            } finally {
                active.compareAndSet(connection, null)
                connection.disconnect()
            }
        }
        override fun close() {
            closed.set(true)
            active.getAndSet(null)?.disconnect()
        }
    }
}
