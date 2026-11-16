package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SentinelAiLocalProtocolTest {
    private val key = ByteArray(32) { it.toByte() } // Test fixture only.
    private fun mac(text: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun engine(
        wrongChallenge: Boolean = false,
        wrongResponse: Boolean = false,
        model: String = SentinelAiLocalEngine.MODEL_SHA256,
        action: String = "NONE",
        onRequest: () -> Unit = {}
    ): SentinelAiLocalEngine = SentinelAiLocalEngine(key, object : SentinelAiLocalEngine.Transport {
        val nonce = "b".repeat(64)
        override fun exchange(path: String, body: String?, headers: Map<String, String>): SentinelAiLocalEngine.Packet {
            if (path.startsWith("/challenge?")) {
                val client = path.substringAfter("client_nonce=")
                assertEquals(64, client.length)
                val proof = mac("server\u0000$client\u0000$nonce\u0000$model")
                return SentinelAiLocalEngine.Packet(200, JSONObject().put("nonce", nonce)
                    .put("model_sha256", model).put("proof", if (wrongChallenge) "0".repeat(64) else proof).toString(), null)
            }
            assertEquals("/infer", path)
            assertEquals(nonce, headers["X-Sentinel-Nonce"])
            assertEquals(mac("client\u0000$nonce\u0000$body"), headers["X-Sentinel-Mac"])
            assertEquals("question privée", JSONObject(body!!).getString("message"))
            onRequest()
            val raw = JSONObject().put("reply", "réponse de test").put("action", action).toString()
            return SentinelAiLocalEngine.Packet(200, raw, if (wrongResponse) "0".repeat(64) else mac("response\u0000$nonce\u0000$raw"))
        }
        override fun close() {}
    })
    @Test fun authenticatedReplyContainsOnlyARecommendation() {
        engine().use { assertEquals(SentinelAiLocalEngine.Recommendation("réponse de test", "NONE"), it.infer("question privée")) }
    }
    @Test fun wrongServerProofNeverSendsUserText() {
        var sent = false
        engine(wrongChallenge = true, onRequest = { sent = true }).use {
            assertThrows(IOException::class.java) { it.infer("question privée") }
        }
        assertFalse(sent)
    }
    @Test fun unknownModelNeverSendsUserText() {
        var sent = false
        engine(model = "c".repeat(64), onRequest = { sent = true }).use {
            assertThrows(IOException::class.java) { it.infer("question privée") }
        }
        assertFalse(sent)
    }
    @Test fun unauthenticatedReplyIsRejected() {
        engine(wrongResponse = true).use { assertThrows(IOException::class.java) { it.infer("question privée") } }
    }
    @Test fun unregisteredActionIsRejected() {
        engine(action = "SEND_SMS").use { assertThrows(IOException::class.java) { it.infer("question privée") } }
    }
    @Test fun closeDuringRequestDiscardsReply() {
        lateinit var client: SentinelAiLocalEngine
        client = engine(onRequest = { client.close() })
        assertThrows(IOException::class.java) { client.infer("question privée") }
    }
    @Test fun closeBeforeRequestRefusesInference() {
        val client = engine(); client.close()
        assertThrows(IOException::class.java) { client.infer("question privée") }
    }
    @Test fun invalidInputsAreRejectedBeforeTransport() {
        engine().use { client ->
            for (text in listOf("", "x".repeat(513), "a\u0000b")) {
                assertThrows(IllegalArgumentException::class.java) { client.infer(text) }
            }
        }
    }
    @Test fun pairingKeyMustBe256Bits() {
        assertThrows(IllegalArgumentException::class.java) { SentinelAiLocalEngine(ByteArray(31)) }
    }
}
