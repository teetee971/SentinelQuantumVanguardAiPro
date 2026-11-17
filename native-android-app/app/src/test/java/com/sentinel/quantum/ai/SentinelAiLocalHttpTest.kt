package com.sentinel.quantum.ai

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Real loopback sockets and production transport; no inference or authentication is simulated. */
class SentinelAiLocalHttpTest {
    private class Server(private val response: (Socket) -> Unit) : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = listener.localPort
        val started = CountDownLatch(1)
        @Volatile var socket: Socket? = null
        @Volatile var line = ""
        private val worker = Executors.newSingleThreadExecutor()
        init {
            worker.submit {
                try {
                    listener.accept().use { client ->
                        socket = client;client.soTimeout = 2000
                        val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                        line = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume HTTP headers only. */ }
                        started.countDown();response(client)
                    }
                } catch (_: IOException) { /* Expected when a test closes its socket. */ }
                  catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }
        override fun close() { socket?.close();listener.close();worker.shutdownNow() }
    }
    private fun drip(client: Socket) {
        val out = client.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Length: 8192\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
        while (!client.isClosed) { out.write(' '.code);out.flush();Thread.sleep(100) }
    }
    @Test fun slowChallengeCannotExtendTheTotalDeadlineWithDrippedBytes() {
        val server = Server(::drip)
        val engine = SentinelAiLocalEngine(ByteArray(32), SentinelAiLocalEngine.LoopbackTransport(server.port))
        val pool = Executors.newSingleThreadExecutor()
        try {
            val start = System.nanoTime()
            val request = pool.submit<IOException?> {
                try { engine.infer("private fixture");null } catch (error: IOException) { error }
            }
            assertTrue(server.started.await(3, TimeUnit.SECONDS))
            assertNotNull(request.get(5, TimeUnit.SECONDS))
            assertTrue("Challenge deadline exceeded", TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) < 4500)
            assertTrue(server.line.startsWith("GET /challenge?client_nonce="))
        } finally { server.close();engine.close();pool.shutdownNow() }
    }
    @Test fun closeCancelsARealBlockedRead() {
        val server = Server(::drip)
        val engine = SentinelAiLocalEngine(ByteArray(32), SentinelAiLocalEngine.LoopbackTransport(server.port))
        val pool = Executors.newSingleThreadExecutor()
        try {
            val request = pool.submit<Boolean> {
                try { engine.infer("private fixture");false } catch (_: IOException) { true }
            }
            assertTrue(server.started.await(3, TimeUnit.SECONDS))
            engine.close()
            assertTrue(request.get(1, TimeUnit.SECONDS))
            assertThrows(IOException::class.java) { engine.infer("private fixture") }
        } finally { server.close();engine.close();pool.shutdownNow() }
    }
    @Test fun redirectDoesNotReachAnotherLoopbackEndpoint() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { destination ->
            destination.soTimeout = 400
            Server { client ->
                client.getOutputStream().apply {
                    write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${destination.localPort}/stolen\r\n" +
                        "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray());flush()
                }
            }.use { server ->
                SentinelAiLocalEngine(ByteArray(32), SentinelAiLocalEngine.LoopbackTransport(server.port)).use { engine ->
                    assertThrows(IOException::class.java) { engine.infer("private fixture") }
                    assertThrows(SocketTimeoutException::class.java) { destination.accept().close() }
                }
            }
        }
    }
}
