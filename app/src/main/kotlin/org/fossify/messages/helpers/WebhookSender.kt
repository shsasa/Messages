package org.fossify.messages.helpers

import android.content.Context
import android.net.Uri
import android.telephony.SubscriptionManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.models.Message
import org.fossify.messages.models.WebhookPayload
import org.fossify.messages.models.WebhookTestResult

object WebhookSender {
    private const val TIMEOUT_MS = 15000

    private val json = Json { explicitNulls = false }

    private val prettyJson = Json {
        explicitNulls = false
        prettyPrint = true
    }

    fun send(context: Context, eventType: WebhookEventType, message: Message) {
        val address = message.senderPhoneNumber.ifEmpty {
            message.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        } ?: ""
        val senderName = message.senderName.ifEmpty {
            message.participants.firstOrNull()?.name
        } ?: ""
        val contactName = senderName.takeIf { it.isNotEmpty() && it != address }
        send(
            context,
            WebhookPayload(
                eventType = eventType.value,
                address = address,
                contactName = contactName,
                body = message.body.takeIf { it.isNotEmpty() },
                timestamp = message.millis(),
                threadId = message.threadId.takeIf { it != 0L },
                subscriptionId = message.subscriptionId
                    .takeIf { it != -1 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                messageId = message.id.takeIf { it != 0L },
                appPackage = context.packageName
            )
        )
    }

    fun sendOutgoingSms(
        context: Context,
        address: String,
        body: String,
        threadId: Long,
        subId: Int,
        messageId: Long,
    ) {
        ensureBackgroundThread {
            val contactName = context.getNameAndPhotoFromPhoneNumber(address).name
                .takeIf { it != address }
            val payload = WebhookPayload(
                eventType = WebhookEventType.OUTGOING_SMS.value,
                address = address,
                contactName = contactName,
                body = body.takeIf { it.isNotEmpty() },
                timestamp = System.currentTimeMillis(),
                threadId = threadId.takeIf { it != 0L },
                subscriptionId = subId
                    .takeIf { it != -1 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                messageId = messageId.takeIf { it != 0L },
                appPackage = context.packageName
            )
            sendInternal(context, payload)
        }
    }

    fun send(context: Context, payload: WebhookPayload) {
        ensureBackgroundThread {
            sendInternal(context, payload)
        }
    }

    fun previewTest(context: Context): WebhookTestResult {
        val payload = WebhookPayload(
            eventType = WebhookEventType.TEST.value,
            address = "+1234567890",
            body = "This is a test webhook from Fossify Messages.",
            timestamp = System.currentTimeMillis(),
            appPackage = context.packageName
        )
        return buildRequest(context, payload)
    }

    fun executeTest(context: Context, request: WebhookTestResult): WebhookTestResult {
        return executeRequest(context, request)
    }

    @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
    private fun sendInternal(context: Context, payload: WebhookPayload): Boolean {
        val config = context.config
        val urlString = config.webhookUrl.trim()
        if (!config.webhookEnabled || urlString.isBlank() || !shouldSendEvent(config, payload)) {
            return false
        }

        val request = buildRequest(context, payload)
        return executeRequest(context, request).isSuccess
    }

    private fun buildRequest(context: Context, payload: WebhookPayload): WebhookTestResult {
        val config = context.config
        val urlString = config.webhookUrl.trim()
        val body = prettyJson.encodeToString(WebhookPayload.serializer(), payload)

        return when (config.webhookHttpMethod) {
            WEBHOOK_METHOD_GET -> {
                val queryParams = payload.toMap()
                    .filterValues { it != null }
                    .mapValues { it.value.toString() }
                val builder = Uri.parse(urlString).buildUpon()
                queryParams.forEach { (key, value) ->
                    builder.appendQueryParameter(key, value)
                }
                WebhookTestResult(
                    url = builder.build().toString(),
                    method = "GET",
                    headers = buildHeaders(config, null),
                    requestBody = null,
                    queryParameters = queryParams,
                    eventType = payload.eventType
                )
            }
            WEBHOOK_METHOD_PUT -> WebhookTestResult(
                url = urlString,
                method = "PUT",
                headers = buildHeaders(config, body),
                requestBody = prettyPrintJson(body),
                eventType = payload.eventType
            )
            else -> WebhookTestResult(
                url = urlString,
                method = "POST",
                headers = buildHeaders(config, body),
                requestBody = prettyPrintJson(body),
                eventType = payload.eventType
            )
        }
    }

    private fun buildHeaders(config: Config, body: String?): Map<String, String> {
        val headers = mutableMapOf("Accept" to "application/json")
        val token = config.webhookBearerToken
        if (token.isNotBlank()) {
            headers["Authorization"] = "Bearer $token"
        }
        if (body != null) {
            headers["Content-Type"] = "application/json"
        }
        return headers
    }

    private fun shouldSendEvent(config: Config, payload: WebhookPayload): Boolean {
        return when (payload.eventType) {
            WebhookEventType.INCOMING_SMS.value, WebhookEventType.INCOMING_MMS.value -> {
                config.webhookForwardIncoming
            }
            WebhookEventType.OUTGOING_SMS.value, WebhookEventType.OUTGOING_MMS.value -> {
                config.webhookForwardOutgoing
            }
            else -> true
        }
    }

    @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
    private fun executeRequest(context: Context, request: WebhookTestResult): WebhookTestResult {
        var connection: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        return try {
            connection = URL(request.url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = request.method
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
            val stream = if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val duration = System.currentTimeMillis() - start

            request.copy(
                responseCode = responseCode,
                responseHeaders = responseHeaders,
                responseBody = prettyPrintJson(responseBody),
                durationMs = duration,
                isSuccess = responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
            )
        } catch (e: Exception) {
            e.printStackTrace()
            request.copy(
                errorMessage = e.message ?: e.toString(),
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun prettyPrintJson(raw: String): String {
        if (raw.isBlank()) return raw
        return try {
            val element: JsonElement = json.parseToJsonElement(raw)
            prettyJson.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            raw
        }
    }

    private fun WebhookPayload.toMap(): Map<String, Any?> {
        return mapOf(
            "event_type" to eventType,
            "address" to address,
            "contact_name" to contactName,
            "body" to body,
            "timestamp" to timestamp,
            "thread_id" to threadId,
            "subscription_id" to subscriptionId,
            "message_id" to messageId,
            "app_package" to appPackage,
        )
    }
}
