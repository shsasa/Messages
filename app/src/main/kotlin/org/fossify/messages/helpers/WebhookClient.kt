package org.fossify.messages.helpers

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.security.MessageDigest
import org.fossify.messages.models.WebhookTestResult

/**
 * Sends webhook requests. Every request passes [policyError] first, so nothing reaches the
 * network without a valid HTTPS endpoint and a Bearer token. Has no Android dependencies.
 */
object WebhookClient {
    /**
     * A queued message older than this is dropped instead of sent. One-time codes are only
     * useful for a few minutes, so after a long outage old codes must not be delivered.
     */
    const val MAX_MESSAGE_AGE_MS = 10 * 60 * 1000L

    const val REDACTED = "<redacted>"
    const val ERROR_URL = "The webhook URL must be a valid https:// address"
    const val ERROR_TOKEN = "A Bearer token is required"

    private const val TIMEOUT_MS = 15000
    private const val EVENT_ID_LENGTH = 32
    private const val AUTHORIZATION = "Authorization"

    /** Returns why a request must not be sent, or null if it may be sent. */
    fun policyError(url: String, token: String, allowCleartext: Boolean): String? {
        return when {
            !isUrlAllowed(url, allowCleartext) -> ERROR_URL
            token.isBlank() -> ERROR_TOKEN
            else -> null
        }
    }

    /** HTTPS with a host is required. Plain HTTP is accepted only when [allowCleartext] is set. */
    fun isUrlAllowed(url: String, allowCleartext: Boolean): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        val schemeAllowed = scheme == "https" || (allowCleartext && scheme == "http")
        return schemeAllowed && !uri.host.isNullOrBlank()
    }

    /** Stable per message, so the receiver can discard a retry it has already processed. */
    fun eventId(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(EVENT_ID_LENGTH)
    }

    fun isExpired(messageTimestamp: Long, now: Long): Boolean {
        return now - messageTimestamp > MAX_MESSAGE_AGE_MS
    }

    /** The headers shown in previews and copied results. The token itself is never included. */
    fun displayHeaders(eventId: String): Map<String, String> {
        return mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "Idempotency-Key" to eventId,
            AUTHORIZATION to "Bearer $REDACTED",
        )
    }

    /**
     * Sends [request] with the real token. The returned result never contains the token, even
     * if the server echoes it back.
     */
    fun post(request: WebhookTestResult, token: String, allowCleartext: Boolean): WebhookTestResult {
        policyError(request.url, token, allowCleartext)?.let {
            return request.copy(errorMessage = it)
        }

        var connection: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        return try {
            connection = URL(request.url.trim()).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = request.method
            // A redirect could send the token and message to another host or to plain HTTP.
            connection.instanceFollowRedirects = false
            request.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            connection.setRequestProperty(AUTHORIZATION, "Bearer $token")

            if (request.requestBody != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.requestBody.toByteArray()) }
            }

            val responseCode = connection.responseCode
            val responseHeaders = connection.headerFields
                ?.filter { it.key != null }
                ?.mapValues { it.value?.joinToString(", ").orEmpty().replace(token, REDACTED) }
                ?: emptyMap()
            val stream = if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            request.copy(
                responseCode = responseCode,
                responseHeaders = responseHeaders,
                responseBody = responseBody.replace(token, REDACTED),
                durationMs = System.currentTimeMillis() - start,
                isSuccess = responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
            )
        } catch (e: IOException) {
            // Deliberately not logged: nothing about a message may reach logcat.
            request.copy(
                errorMessage = (e.message ?: e.javaClass.simpleName).replace(token, REDACTED),
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            connection?.disconnect()
        }
    }
}
