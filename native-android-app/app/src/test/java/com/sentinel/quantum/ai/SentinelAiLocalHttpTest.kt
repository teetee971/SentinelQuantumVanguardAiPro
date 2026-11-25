package com.sentinel.quantum.ai

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
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
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 64, loopback).use { bait ->
            ServerSocket(0, 64, loopback).use { origin ->
                val served = AtomicReference<String?>(null)
                val worker = Executors.newSingleThreadExecutor()
                worker.submit {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (served.get() == null && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted) {
                        origin.soTimeout = 200
                        val client = try {
                            origin.accept()
                        } catch (_: SocketTimeoutException) {
                            continue
                        } catch (_: IOException) {
                            break
                        }
                        client.use { socket ->
                            val line = requestLine(socket) ?: return@use
                            if (!line.startsWith("GET /challenge?client_nonce=")) return@use
                            served.set(line)
                            socket.getOutputStream().apply {
                                write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${bait.localPort}/stolen\r\n" +
                                    "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                                flush()
                            }
                        }
                    }
                }
                try {
                    val client = loopbackClient()
                    assertFalse(client.followRedirects)
                    assertFalse(client.followSslRedirects)
                    assertFalse(client.retryOnConnectionFailure)
                    SentinelAiLocalEngine(ByteArray(32), SentinelAiLocalEngine.LoopbackTransport(origin.localPort)).use { engine ->
                        assertThrows(IOException::class.java) { engine.infer("private fixture") }
                    }
                    val request = served.get()
                    assertNotNull("the engine challenge never reached the origin listener", request)
                    assertFalse(request!!.contains("/stolen"))
                    val stolen = stolenRequest(bait, 800)
                    assertNull("redirect was followed to $stolen", stolen)
                } finally {
                    origin.close()
                    worker.shutdownNow()
                }
            }
        }
    }

    /** A foreign handshake is not a followed redirect. Only an HTTP request for the bait path is. */
    private fun stolenRequest(bait: ServerSocket, budgetMs: Long): String? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs)
        while (System.nanoTime() < deadline) {
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).toInt().coerceAtLeast(1)
            bait.soTimeout = remaining
            val client = try {
                bait.accept()
            } catch (_: SocketTimeoutException) {
                return null
            }
            client.use { socket ->
                val line = requestLine(socket)
                if (line != null && line.contains("/stolen")) return line
            }
        }
        return null
    }

    private fun requestLine(socket: Socket): String? {
        socket.soTimeout = 200
        return try {
            socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
        } catch (_: IOException) {
            null
        }
    }

    private fun loopbackClient(): OkHttpClient {
        val type = Class.forName("com.sentinel.quantum.ai.SentinelAiLocalEngine\$LoopbackTransport")
        val field = type.getDeclaredField("HTTP")
        field.isAccessible = true
        return field.get(null) as OkHttpClient
    }
}
