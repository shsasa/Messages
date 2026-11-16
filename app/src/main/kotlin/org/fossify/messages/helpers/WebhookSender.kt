package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Sms
import android.telephony.SubscriptionManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import org.fossify.messages.BuildConfig
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.models.WebhookPayload
import org.fossify.messages.models.WebhookTestResult

/**
 * Forwards incoming SMS from approved senders to the configured webhook.
 *
 * Policy: incoming SMS only, approved senders only, HTTPS POST only, Bearer authentication.
 * Message bodies are never written to logs, and the work queue only stores message IDs.
 */
object WebhookSender {
    enum class Delivery { SENT, RETRY, DROPPED }

    private const val TIMEOUT_MS = 15000
    private const val RETRY_DELAY_SECONDS = 30L
    private const val HTTP_METHOD = "POST"
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val WORK_NAME_PREFIX = "webhook-sms-"
    private const val EVENT_ID_LENGTH = 32

    private val prettyJson = Json {
        explicitNulls = false
        prettyPrint = true
    }

    /** HTTPS is required. Plain HTTP is accepted only by debug builds, for local testing. */
    fun isUrlAllowed(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://", ignoreCase = true) ||
            (BuildConfig.DEBUG && trimmed.startsWith("http://", ignoreCase = true))
    }

    /**
     * Queues an incoming SMS for delivery if forwarding is on and the sender is approved.
     * The unique work name stops the same message from being queued twice.
     */
    fun enqueueIncomingSms(context: Context, messageId: Long, address: String) {
        if (messageId == 0L || !shouldForward(context, address)) {
            return
        }

        val request = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setInputData(workDataOf(WebhookWorker.KEY_MESSAGE_ID to messageId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME_PREFIX + messageId, ExistingWorkPolicy.KEEP, request)
    }

    /** Called by [WebhookWorker]. Reads the message again and re-checks the policy before sending. */
    fun deliver(context: Context, messageId: Long): Delivery {
        val payload = loadIncomingSms(context, messageId)
        if (payload == null || !shouldForward(context, payload.address)) {
            return Delivery.DROPPED
        }

        val result = executeRequest(buildRequest(context, payload))
        val code = result.responseCode
        return when {
            result.isSuccess -> Delivery.SENT
            code == null -> Delivery.RETRY
            code == HttpURLConnection.HTTP_CLIENT_TIMEOUT || code == HTTP_TOO_MANY_REQUESTS -> Delivery.RETRY
            code >= HttpURLConnection.HTTP_INTERNAL_ERROR -> Delivery.RETRY
            else -> Delivery.DROPPED
        }
    }

    fun previewTest(context: Context): WebhookTestResult {
        val timestamp = System.currentTimeMillis()
        val payload = WebhookPayload(
            eventType = WebhookEventType.TEST.value,
            eventId = eventId(context.packageName, "test", timestamp.toString()),
            address = "+1234567890",
            body = "This is a test webhook from Fossify Messages.",
            timestamp = timestamp,
            appPackage = context.packageName
        )
        return buildRequest(context, payload)
    }

    fun executeTest(context: Context, request: WebhookTestResult): WebhookTestResult {
        if (!context.config.webhookEnabled) {
            return request.copy(errorMessage = "Webhook forwarding is disabled")
        }
        if (!isUrlAllowed(request.url)) {
            return request.copy(errorMessage = "The webhook URL must start with https://")
        }
        return executeRequest(request)
    }

    private fun shouldForward(context: Context, address: String): Boolean {
        val config = context.config
        return config.webhookEnabled &&
            config.webhookForwardIncoming &&
            isUrlAllowed(config.webhookUrl) &&
            WebhookSenderFilter.isApproved(address, config.webhookApprovedSenders)
    }

    private fun loadIncomingSms(context: Context, messageId: Long): WebhookPayload? {
        val projection = arrayOf(Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.THREAD_ID, Sms.SUBSCRIPTION_ID)
        val selection = "${Sms._ID} = ? AND ${Sms.TYPE} = ?"
        val selectionArgs = arrayOf(messageId.toString(), Sms.MESSAGE_TYPE_INBOX.toString())
        val cursor = context.contentResolver.query(Sms.CONTENT_URI, projection, selection, selectionArgs, null)
        return cursor?.use {
            if (!it.moveToFirst()) {
                return null
            }

            val address = it.getString(it.getColumnIndexOrThrow(Sms.ADDRESS)).orEmpty()
            val timestamp = it.getLong(it.getColumnIndexOrThrow(Sms.DATE))
            val contactName = context.getNameAndPhotoFromPhoneNumber(address).name
            WebhookPayload(
                eventType = WebhookEventType.INCOMING_SMS.value,
                eventId = eventId(context.packageName, messageId.toString(), timestamp.toString(), address),
                address = address,
                contactName = contactName.takeIf { name -> name.isNotEmpty() && name != address },
                body = it.getString(it.getColumnIndexOrThrow(Sms.BODY))?.takeIf { body -> body.isNotEmpty() },
                timestamp = timestamp,
                threadId = it.getLong(it.getColumnIndexOrThrow(Sms.THREAD_ID)).takeIf { id -> id != 0L },
                subscriptionId = it.getInt(it.getColumnIndexOrThrow(Sms.SUBSCRIPTION_ID))
                    .takeIf { id -> id != -1 && id != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                messageId = messageId,
                appPackage = context.packageName
            )
        }
    }

    /** Stable per message, so the receiver can discard a retry it has already processed. */
    private fun eventId(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(EVENT_ID_LENGTH)
    }

    private fun buildRequest(context: Context, payload: WebhookPayload): WebhookTestResult {
        val config = context.config
        val body = prettyJson.encodeToString(WebhookPayload.serializer(), payload)
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "Idempotency-Key" to payload.eventId,
        )
        val token = config.webhookBearerToken
        if (token.isNotBlank()) {
            headers["Authorization"] = "Bearer $token"
        }

        return WebhookTestResult(
            url = config.webhookUrl.trim(),
            method = HTTP_METHOD,
            headers = headers,
            requestBody = body,
            eventType = payload.eventType
        )
    }

    private fun executeRequest(request: WebhookTestResult): WebhookTestResult {
        var connection: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        return try {
            connection = URL(request.url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = request.method
            // A redirect could send the token and message to another host or to plain HTTP.
            connection.instanceFollowRedirects = false
            request.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            if (request.requestBody != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.requestBody.toByteArray()) }
            }

            val responseCode = connection.responseCode
            val responseHeaders = connection.headerFields
                ?.filter { it.key != null }
                ?.mapValues { it.value?.joinToString(", ") ?: "" }
                ?: emptyMap()
            val stream = if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            request.copy(
                responseCode = responseCode,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                durationMs = System.currentTimeMillis() - start,
                isSuccess = responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
            )
        } catch (e: IOException) {
            // Deliberately not logged: nothing about a message may reach logcat.
            request.copy(
                errorMessage = e.message ?: e.javaClass.simpleName,
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            connection?.disconnect()
        }
    }
}
