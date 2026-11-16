package org.fossify.messages.helpers

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.fossify.messages.models.WebhookTestResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebhookClientTest {
    private val token = "synthetic-test-token"
    private val requests = CopyOnWriteArrayList<String>()
    private lateinit var server: ServerSocket

    /** A loopback server that records every request it receives and echoes it back. */
    @Before
    fun startServer() {
        server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (true) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                socket.use { respond(it) }
            }
        }
    }

    private fun respond(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val length = head.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.toInt() ?: 0
        val body = CharArray(length)
        var read = 0
        while (read < length) {
            val count = reader.read(body, read, length - read)
            if (count < 0) break
            read += count
        }

        val received = head.joinToString("\n") + "\n" + String(body, 0, read)
        requests.add(received)
        val bytes = received.toByteArray()
        val response = "HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(response.toByteArray())
            write(bytes)
            flush()
        }
    }

    @After
    fun stopServer() {
        server.close()
    }

    private fun request(url: String = "http://127.0.0.1:${server.localPort}/hook") = WebhookTestResult(
        url = url,
        method = "POST",
        headers = WebhookClient.displayHeaders("event-1"),
        requestBody = """{"body":"Synthetic code 000000"}""",
        eventType = "incoming_sms",
    )

    @Test
    fun requestWithTokenIsSentWithRealAuthorizationHeader() {
        val result = WebhookClient.post(request(), token, allowCleartext = true)

        assertTrue(result.isSuccess)
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("Authorization: Bearer $token"))
        assertTrue(requests[0].contains("Synthetic code 000000"))
    }

    @Test
    fun missingTokenCausesNoNetworkRequest() {
        for (missing in listOf("", "   ")) {
            val result = WebhookClient.post(request(), missing, allowCleartext = true)

            assertFalse(result.isSuccess)
            assertNull(result.responseCode)
            assertEquals(WebhookClient.ERROR_TOKEN, result.errorMessage)
        }
        assertEquals(0, requests.size)
    }

    @Test
    fun plainHttpCausesNoNetworkRequestInReleaseMode() {
        val result = WebhookClient.post(request(), token, allowCleartext = false)

        assertEquals(WebhookClient.ERROR_URL, result.errorMessage)
        assertEquals(0, requests.size)
    }

    @Test
    fun resultNeverContainsTheTokenEvenWhenTheServerEchoesIt() {
        val result = WebhookClient.post(request(), token, allowCleartext = true)

        assertTrue(result.responseBody!!.contains("Authorization: Bearer ${WebhookClient.REDACTED}"))
        assertFalse(result.toString().contains(token))
    }

    @Test
    fun displayHeadersNeverContainTheToken() {
        assertEquals("Bearer ${WebhookClient.REDACTED}", WebhookClient.displayHeaders("e")["Authorization"])
    }

    @Test
    fun onlyValidHttpsUrlsAreAllowedInReleaseMode() {
        assertTrue(WebhookClient.isUrlAllowed("https://n8n.example.com/webhook/sms", false))
        assertTrue(WebhookClient.isUrlAllowed(" HTTPS://n8n.example.com/x ", false))
        assertFalse(WebhookClient.isUrlAllowed("http://n8n.example.com/x", false))
        assertFalse(WebhookClient.isUrlAllowed("https://", false))
        assertFalse(WebhookClient.isUrlAllowed("https:///path", false))
        assertFalse(WebhookClient.isUrlAllowed("n8n.example.com/x", false))
        assertFalse(WebhookClient.isUrlAllowed("ftp://n8n.example.com/x", false))
        assertFalse(WebhookClient.isUrlAllowed("https://exa mple.com", false))
        assertFalse(WebhookClient.isUrlAllowed("", false))
        assertTrue(WebhookClient.isUrlAllowed("http://10.0.2.2:8791/hook", true))
    }

    @Test
    fun messagesExpireAfterTheDocumentedAge() {
        val received = 1_000_000L
        val limit = WebhookClient.MAX_MESSAGE_AGE_MS

        assertEquals(10 * 60 * 1000L, limit)
        assertFalse(WebhookClient.isExpired(received, received))
        assertFalse(WebhookClient.isExpired(received, received + limit))
        assertTrue(WebhookClient.isExpired(received, received + limit + 1))
        assertFalse(WebhookClient.isExpired(received, received - 5_000))
    }
}
